package me.alex.dpl;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.AutoRun;
import me.alex.dpl.annotation.DependencyConstructor;
import me.alex.dpl.annotation.Inject;
import me.alex.dpl.pojo.Dependency;
import org.atteo.classindex.ClassIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

public final class DependencyManager {

    private static DependencyManager instance;
    private static boolean alreadyInitialized = false;
    private final ExecutorService executor;
    private final Map<Class<?>, Object> createdObjects;
    private final Logger logger = LoggerFactory.getLogger(DependencyManager.class);
    private List<Dependency> sortedDependencies;

    private DependencyManager() {
        this.createdObjects = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool();
    }

    public static DependencyManager getDependencyManager() {
        //Singleton
        if (instance == null) {
            instance = new DependencyManager();
        }
        return instance;
    }

    public void init() {
        if (alreadyInitialized) throw new IllegalStateException("Dependency already initialized");
        alreadyInitialized = true;

        Instant start = Instant.now();
        logger.info("Loading classes, dependencies, fields and methods...");
        indexClasses();
        logger.info("Generating load order...");
        logger.info(printClassesAndInfo());
        logger.info("Creating instances...");
        createInstances();
        logger.info("Injecting fields...");
        injectFields();
        logger.info("Running methods...");
        runMethods();
        logger.info("Finished in " + (Instant.now().toEpochMilli() - start.toEpochMilli()) + "ms");

        start = Instant.now();
        executor.shutdown();

        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Error while waiting for executor to shutdown", e);
        }
        logger.info("Shutdown executor in " + (Instant.now().toEpochMilli() - start.toEpochMilli()) + "ms");
    }

    private void indexClasses() {
        Iterable<Class<?>> classes = getClasses();
        List<Dependency> dependencies = new ArrayList<>();

        //TODO: use parallel if possible
        StreamSupport.stream(classes.spliterator(), false)
                .map(this::computeClass)
                .forEach(dependencies::add);

        sortedDependencies = sortDependencies(dependencies);
    }

    public synchronized void addDependency(Object object) {
        addDependency(object.getClass(), object);
    }

    public synchronized Object getDependency(Class<?> clazz) {
        if (!createdObjects.containsKey(clazz)) {
            throw new RuntimeException("Dependency " + clazz.getName() + " does not exist");
        }
        return createdObjects.get(clazz);
    }

    public synchronized void addDependency(Class<?> clazz, Object object) {
        if (alreadyInitialized)
            throw new IllegalStateException("Dependency already initialized. You can't add new dependencies after initialization");
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

        List<Dependency.AutoRunMethod> methodErrors = checkMethods(klass, methods);
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
        return ClassIndex.getAnnotated(AutoLoadable.class, jvmClassLoader);
    }

    private String printClassesAndInfo() {
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
        return builder.toString();
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

    private List<Dependency.AutoRunMethod> checkMethods(Class<?> klass, List<Dependency.AutoRunMethod> methods) {
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

    /*private void hasCyclicDependency() {
        for (Dependency dep : dependencies) {
            for (Dependency dep1 : dependencies) {
                if (dep.getDependencies().contains(dep1.getClazz())) {
                    throw new RuntimeException("Class " + dep.getClazz().getName() + " depends on " + dep1.getClazz().getName() + " but " + dep1.getClazz().getName() + " also depends on " + dep.getClazz().getName());
                }
            }
        }
    }*/


    private void generateLoadOrder() {
        //sort the dependencies by the classes they need in correct order
        /*dependencies.sort(Dependency::compareTo);

        AtomicInteger index = new AtomicInteger();
        for (Dependency dependency : dependencies) {
            System.out.println(index.getAndIncrement() + ": " + dependency.getClazz().getName() + " -> " + dependency.getDependencies().size() + " dependencies");
        }*/
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
