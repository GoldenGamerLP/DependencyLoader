package me.alex.dpl;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.AutoRun;
import me.alex.dpl.annotation.DependencyConstructor;
import me.alex.dpl.annotation.Inject;
import me.alex.dpl.pojo.Dependency;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DependencyManager {

    private static DependencyManager instance;
    private final Set<String> packageNames;
    private final StackWalker walker;
    private final AtomicBoolean alreadyInit;
    private final ExecutorService executor;
    private final Map<Class<?>, Object> createdObjects;
    private final Logger logger = LoggerFactory.getLogger(DependencyManager.class);
    private List<Dependency> sortedDependencies;

    private DependencyManager() {
        walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        packageNames = new HashSet<>();
        alreadyInit = new AtomicBoolean(false);
        executor = Executors.newCachedThreadPool();
        createdObjects = new ConcurrentHashMap<>();
    }

    public static synchronized DependencyManager getDependencyManager() {
        if (instance == null) {
            instance = new DependencyManager();
        }

        String packageName = instance.walker.getCallerClass().getPackageName();
        instance.packageNames.add(packageName);

        return instance;
    }

    public void init(final boolean printInfo) {
        if (alreadyInit.get()) throw new IllegalStateException("Dependency already initialized");
        alreadyInit.compareAndExchange(false, true);

        Instant start = Instant.now();
        logger.info("Loading classes, dependencies, fields and methods from packages: {}", packageNames);
        indexClasses();
        logger.info("Indexed classes and generated load order in... {}", differenceInstants(start));
        createInstances();
        logger.info("Created instances in... {}", differenceInstants(start));
        injectFields();
        logger.info("Injected fields in... {}", differenceInstants(start));
        runMethods();
        logger.info("Executed methods in... {}", differenceInstants(start));
        logger.info("Finished in {}", differenceInstants(start));

        if (printInfo) printClassesAndInfo();

        this.shutdown();
    }

    private void shutdown() {
        Instant start = Instant.now();

        createdObjects.clear();
        sortedDependencies.clear();
        packageNames.clear();

        logger.info("Shutting down executor...");
        executor.shutdown();

        try {
            boolean isShutdown = executor.awaitTermination(5, TimeUnit.SECONDS);
            if (!isShutdown) logger.warn("Executor did not shutdown in time");
        } catch (InterruptedException e) {
            logger.error("Error while waiting for executor to shutdown", e);
        }
        logger.info("Shutdown executor in " + (Instant.now().toEpochMilli() - start.toEpochMilli()) + "ms");
    }

    private String differenceInstants(Instant last) {
        return (Instant.now().toEpochMilli() - last.toEpochMilli()) + "ms";
    }

    private void indexClasses() {
        Iterable<Class<?>> classes = getClasses();
        List<Dependency> dependencies = new ArrayList<>();

        for (Class<?> clazz : classes) {
            dependencies.add(computeClass(clazz));
        }

        sortedDependencies = sortDependencies(dependencies);
    }

    /**
     * Add a Dependency object to the list of dependencies. Note: <b>can only be used before {@link DependencyManager#init(boolean)}</b>
     *
     * @param object Dependency object
     */
    public synchronized void addDependency(Object object) {
        addDependency(object.getClass(), object);
    }

    /**
     * Returns an Object of the given class. Note: <b>cMay throw a RuntimeException if the Dependency was not found</b>
     *
     * @param clazz Class of the dependency
     * @return Object of the given class
     */
    public synchronized Object getDependency(Class<?> clazz) {
        if (!createdObjects.containsKey(clazz)) {
            throw new RuntimeException("Dependency " + clazz.getName() + " does not exist");
        }
        return createdObjects.get(clazz);
    }

    /**
     * Add a Dependency to list of dependencies. Note: <b>can only be used before {@link DependencyManager#init(boolean)}</b>
     *
     * @param clazz  Class of the dependency
     * @param object Object of the dependency
     */
    public synchronized void addDependency(Class<?> clazz, Object object) {
        if (alreadyInit.get())
            throw new IllegalStateException("Dependency already initialized. You can't add new dependencies after initialization");
        if (!clazz.equals(object.getClass()))
            throw new IllegalArgumentException("Class and object class are not equal");

        if (createdObjects.containsKey(clazz)) {
            throw new RuntimeException("Dependency " + clazz.getName() + " already exists");
        }
        createdObjects.put(clazz, object);
    }

    /**
     * Returns a list with sorted dependencies. Uses Topological sorting.
     *
     * @param dependencies List of dependencies
     * @return Sorted list of dependencies
     */
    private List<Dependency> sortDependencies(List<Dependency> dependencies) {
        List<Dependency> sorted = new ArrayList<>();
        Set<Class<?>> visited = new HashSet<>();

        Stack<Dependency> stack = new Stack<>();
        for (Dependency dependency : dependencies) {
            if (!visited.contains(dependency.getClazz())) {
                stack.push(dependency);
                while (!stack.isEmpty()) {
                    Dependency current = stack.peek();
                    visited.add(current.getClazz());

                    boolean allDependenciesVisited = true;
                    for (Class<?> clazz : current.getDependencies()) {
                        Dependency dep = findDependency(dependencies, clazz);
                        if (dep == null) continue;
                        if (dep.getDependencies().contains(current.getClazz())) {
                            throw new IllegalArgumentException("Cyclic dependency found between " + current.getClazz().getName() + " and " + dep.getClazz().getName());
                        }
                        if (!visited.contains(clazz)) {
                            stack.push(dep);
                            allDependenciesVisited = false;
                        }
                    }

                    if (allDependenciesVisited) {
                        Dependency pop = stack.pop();
                        if (!sorted.contains(pop)) {
                            sorted.add(pop);
                        }
                    }
                }
            }
        }

        return sorted;
    }

    private Dependency findDependency(List<Dependency> dependencies, Class<?> clazz) {
        for (Dependency dependency : dependencies) {
            if (dependency.getClazz().equals(clazz)) {
                return dependency;
            }
        }
        return null;
    }

    private Dependency computeClass(Class<?> klass) {
        List<Constructor<?>> constructor = Arrays.stream(klass.getConstructors())
                .filter(constructor1 -> constructor1.isAnnotationPresent(DependencyConstructor.class))
                .toList();
        if (constructor.size() != 1) {
            throw new RuntimeException("Class " + klass.getName() + " has no or more than one constructor annotated with @DependencyConstructor");
        }

        Constructor<?> cons = constructor.get(0);
        List<Class<?>> parameterTypes = List.of(cons.getParameterTypes());
        List<Field> fields = Arrays.stream(klass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Inject.class))
                .toList();
        List<Dependency.AutoRunMethod> methods = new ArrayList<>();

        for (Method method : klass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(AutoRun.class)) {
                AutoRun annotation = method.getAnnotation(AutoRun.class);
                Dependency.AutoRunMethod met = new Dependency.AutoRunMethod(method, annotation.priority(), annotation.async());
                methods.add(met);
            }
        }

        List<Dependency.AutoRunMethod> methodErrors = checkMethods(methods);
        List<Class<?>> errors = checkParameters(klass, parameterTypes);
        List<Field> fieldErrors = checkFields(klass, fields);

        if (!errors.isEmpty()) {
            throw new RuntimeException("Class " + klass.getName() + " has invalid parameters: " + errors);
        }
        if (!fieldErrors.isEmpty()) {
            throw new RuntimeException("Class " + klass.getName() + " has invalid fields: " + fieldErrors);
        }
        if (!methodErrors.isEmpty()) {
            throw new RuntimeException("Class " + klass.getName() + " has invalid methods: " + methodErrors);
        }

        //sort the methods by priority
        Collections.sort(methods);

        //force add the class to the load order
        return new Dependency(klass, cons, parameterTypes, fields, methods);
    }

    private Iterable<Class<?>> getClasses() {
        //get all classes annotated with AutoLoadable over the whole jvm
        ClassLoader jvmClassLoader = ClassLoader.getSystemClassLoader();
        //
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .addClassLoaders(jvmClassLoader)
                .setParallel(true)
                .setScanners(Scanners.TypesAnnotated)
                .forPackages(packageNames.toArray(new String[0]))
        );

        return reflections.getTypesAnnotatedWith(AutoLoadable.class);
    }

    private void printClassesAndInfo() {
        StringBuilder builder = new StringBuilder("Classes to load:\n");
        Dependency[] dependencies = sortedDependencies.toArray(new Dependency[0]);
        for (int i = 0; i < dependencies.length; i++) {
            Dependency dependency = dependencies[i];
            String crrClazz = dependency.getClazz().getName();
            String dependencyNames = Arrays.toString(dependency.getDependencies().stream().map(Class::getName).toArray());
            String injectionNames = Arrays.toString(dependency.getInjectionFields().stream().map(field -> Modifier.toString(field.getModifiers()) + " " + field.getType().getSimpleName() + " " + field.getName()).toArray());
            String injectionMethodNames = Arrays.toString(dependency.getInjectionMethods().stream()
                    .map(method -> method.getPriority() + ". " + crrClazz + "#" + method.getMethod().getName())
                    .toArray());

            builder.append(i + 1).append(". ").append(dependency.getClazz().getName())
                    .append("\n")
                    .append("   Constructor: ").append(dependency.getConstructor().getName())
                    .append("\n")
                    .append("   Dependencies: ").append(dependencyNames)
                    .append("\n")
                    .append("   Injection fields: ").append(injectionNames)
                    .append("\n")
                    .append("   Injection methods: ").append(injectionMethodNames)
                    .append("\n");
        }
        logger.info(builder.toString());
    }

    private void runMethods() {
        Dependency[] dependencies = sortedDependencies.toArray(new Dependency[0]);
        for (Dependency dependency : dependencies) {
            Object object = createdObjects.get(dependency.getClazz());
            if (object == null) {
                throw new RuntimeException("Failed to run methods in class " + dependency.getClazz().getName() + " because the object is null");
            }
            for (Dependency.AutoRunMethod autoRunMethod : dependency.getInjectionMethods()) {
                if (autoRunMethod.isAsync()) {
                    executor.execute(() -> runMethod(autoRunMethod, object));
                } else {
                    runMethod(autoRunMethod, object);
                }
            }
        }
    }

    private void runMethod(Dependency.AutoRunMethod autoRunMethod, Object object) {
        Method method = autoRunMethod.getMethod();
        try {
            method.setAccessible(true);
            method.invoke(object);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to run method " + method.getName() + " in class " + method.getClass());
        }
    }

    private List<Dependency.AutoRunMethod> checkMethods(List<Dependency.AutoRunMethod> methods) {
        List<Dependency.AutoRunMethod> methodErrors = new ArrayList<>();
        for (Dependency.AutoRunMethod method : methods) {
            if (method.getMethod().getParameterCount() != 0) {
                methodErrors.add(method);
            }
            if (!method.getMethod().getReturnType().equals(void.class)) {
                methodErrors.add(method);
            }
        }
        return methodErrors;
    }

    private void injectFields() {
        Dependency[] dependencies = sortedDependencies.toArray(new Dependency[0]);
        for (Dependency dependency : dependencies) {
            Object object = createdObjects.get(dependency.getClazz());
            for (Field field : dependency.getInjectionFields()) {
                Object fieldObj = createdObjects.get(field.getType());
                if (fieldObj == null) {
                    throw new RuntimeException("Failed to inject field " + field.getName() + " in class " + dependency.getClazz().getName() + ". Forgot to add a Dependency?");
                }
                try {
                    field.setAccessible(true);
                    field.set(object, fieldObj);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to inject field " + field.getName() + " in class " + dependency.getClazz().getName());
                }
            }
        }
    }

    private List<Field> checkFields(Class<?> klass, List<Field> fields) {
        List<Field> fieldErrors = new ArrayList<>();
        for (Field field : fields) {
            if (field.getType().equals(klass)) {
                fieldErrors.add(field);
            }
        }
        return fieldErrors;
    }

    private List<Class<?>> checkParameters(Class<?> clazz, List<Class<?>> parameterTypes) {
        //check parameters for @AutoLoadable and for any constructor that has @DependencyConstructor
        List<Class<?>> dependenciesError = new ArrayList<>();
        for (Class<?> parameterType : parameterTypes) {
            //Continue because the dependency is already there, no need to check it again
            if (createdObjects.containsKey(parameterType)) {
                continue;
            }

            boolean hasDependencyConstructor = Arrays.stream(parameterType.getConstructors())
                    .anyMatch(constructor -> constructor.isAnnotationPresent(DependencyConstructor.class));
            boolean hasInjectDependency = parameterType.isAnnotationPresent(AutoLoadable.class);
            if (!hasDependencyConstructor && !hasInjectDependency) {
                dependenciesError.add(parameterType);
            } else {
                //check if parametertype has clazz as parameter
                if (parameterType.equals(clazz)) {
                    throw new RuntimeException("Class " + clazz.getName() + " has a circular dependency");
                }
            }
        }
        return dependenciesError;
    }

    private void createInstances() {
        Dependency[] dependencies = sortedDependencies.toArray(new Dependency[0]);
        for (Dependency dependency : dependencies) {
            if (createdObjects.containsKey(dependency.getClazz())) {
                System.out.println("Class " + dependency.getClazz().getName() + " already created");
                continue;
            }
            Object[] parameters = new Object[dependency.getConstructorParameters().size()];
            for (int i = 0; i < parameters.length; i++) {
                Class<?> depClass = dependency.getConstructorParameters().get(i);
                Object para = createdObjects.get(depClass);
                if (para == null) {
                    throw new RuntimeException("Dependency " + depClass.getName() + " not found");
                }
                parameters[i] = para;
            }
            Object instance = createInstance(dependency, parameters);
            createdObjects.put(dependency.getClazz(), instance);
        }
    }

    private Object createInstance(Dependency dependency, Object... parameters) {
        try {
            return dependency.getConstructor().newInstance(parameters);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Error while creating instance of class " + dependency.getClazz().getName(), e);
        }
    }
}
