package me.alex;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.AutoRun;
import me.alex.annotation.DependencyConstructor;
import me.alex.annotation.Inject;
import me.alex.pojo.Dependency;
import me.alex.test.Wichtig;
import org.atteo.classindex.ClassIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public final class DependencyManager {

    /*private final Comparator<Dependency> dependenciesLoadOrder = (o1, o2) -> {
        if (o1.getDependencies().contains(o2.getClazz()) && o2.getDependencies().contains(o1.getClazz())) {
            throw new RuntimeException("Cyclic dependency between " + o1.getClazz().getName() + " and " + o2.getClazz().getName());
        }

        //Sort after if the class is a dependency of the other class
        return o1.getDependencies().contains(o2.getClazz()) ? 1 : -1;
    };*/
    private final Comparator<Dependency> dependenciesLoadOrder = (dependency1, dependency2) -> {
        Class<?> class1 = dependency1.getClazz();
        Class<?> class2 = dependency2.getClazz();

        if (class1.equals(class2)) {
            throw new RuntimeException("Cyclic dependency between " + class1.getName() + " and " + class2.getName());
        }

        List<Class<?>> dependencies1 = dependency1.getDependencies();
        List<Class<?>> dependencies2 = dependency2.getDependencies();

        boolean hasDependency1On2 = dependencies1.contains(class2);
        boolean hasDependency2On1 = dependencies2.contains(class1);

        if (hasDependency1On2 && !hasDependency2On1) {
            return -1; // dependency1 ist abhängig von dependency2, dependency2 sollte zuerst sortiert werden
        } else if (hasDependency2On1 && !hasDependency1On2) {
            return 1; // dependency2 ist abhängig von dependency1, dependency1 sollte zuerst sortiert werden
        } else {
            return 0; // Keine direkte Abhängigkeit, Reihenfolge spielt keine Rolle
        }
    };

    private final ExecutorService executor;
    private final List<Dependency> dependencies;
    private final Map<Class<?>, Object> createdObjects;
    private final Logger logger = LoggerFactory.getLogger(DependencyManager.class);
    private static DependencyManager instance;

    public static DependencyManager getDependencyManager() {
        //Singleton
        if(instance == null) {
            instance = new DependencyManager();
        }
        return instance;
    }

    private DependencyManager() {
        this.dependencies = new CopyOnWriteArrayList<>();
        this.createdObjects = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool();
    }

    public void init() {
        Instant start = Instant.now();
        Iterable<Class<?>> classes = getClasses();
        logger.info("Loading classes, dependencies, fields and methods...");
        //computeDependencies(classes.spliterator(), 2, this::loadClasses);
        classes.forEach(this::loadClasses);
        logger.info("Generating load order...");
        generateLoadOrder();
        logger.info(printClassesAndInfo());
        logger.info("Creating instances...");
        createInstances();
        logger.info("Injecting fields...");
        injectFields();
        logger.info("Running methods...");
        runMethods();
        logger.info("Finished in " + (Instant.now().toEpochMilli() - start.toEpochMilli()) + "ms");
    }

    public synchronized void addDependency(Class<?> clazz, Object object) {
        if (createdObjects.containsKey(clazz)) {
            throw new RuntimeException("Dependency " + clazz.getName() + " already exists");
        }
        createdObjects.put(clazz, object);
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

    private void computeDependencies(Spliterator<Class<?>> classes, int parallelismCount, Consumer<Class<?>> spliteratorConsumer) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int parallelism = parallelismCount;
        for(Spliterator<Class<?>> subIterator; (subIterator = classes.trySplit()) != null ; parallelism--) {
            System.out.println(parallelism);


            //parallel computeation
            subIterator.forEachRemaining(spliteratorConsumer);
        }
    }

    private void loadClasses(Class<?> klass) {
        //Use spliterator to iterate over classes more efficiently
            if(createdObjects.containsKey(klass)) return;

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
            List<Method> methods = new ArrayList<>(Arrays.stream(klass.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(AutoRun.class))
                    .toList());

            List<Method> methodErrors = checkMethods(klass, methods);
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
            methods.sort(Comparator.comparingInt(method -> method.getAnnotation(AutoRun.class).priority()));

            dependencies.add(new Dependency(klass, cons, parameterTypes, fields, methods));
    }

    private Iterable<Class<?>> getClasses() {
        return ClassIndex.getAnnotated(AutoLoadable.class);
    }

    private String printClassesAndInfo() {
        StringBuilder builder = new StringBuilder("Classes to load:\n");
        for (int i = 0; i < dependencies.size(); i++) {
            Dependency dependency = dependencies.get(i);
            String crrClazz = dependency.getClazz().getName();
            String dependencyNames = Arrays.toString(dependency.getDependencies().stream().map(Class::getName).toArray());
            String injectionNames = Arrays.toString(dependency.getInjectionFields().stream().map(field -> Modifier.toString(field.getModifiers()) + " " + field.getType().getSimpleName() + " " + field.getName()).toArray());
            String injectionMethodNames = Arrays.toString(dependency.getInjectionMethods().stream()
                    .map(method -> method.getAnnotation(AutoRun.class).priority() + ". " + crrClazz + "#" + method.getName())
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
        for (Dependency dependency : dependencies) {
            Object object = createdObjects.get(dependency.getClazz());
            if (object == null) {
                throw new RuntimeException("Failed to run methods in class " + dependency.getClazz().getName() + " because the object is null");
            }
            for (Method method : dependency.getInjectionMethods()) {
                try {
                    method.setAccessible(true);
                    method.invoke(object);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException("Failed to run method " + method.getName() + " in class " + dependency.getClazz().getName());
                }
            }
        }
    }

    private List<Method> checkMethods(Class<?> klass, List<Method> methods) {
        List<Method> methodErrors = new ArrayList<>();
        for (Method method : methods) {
            if (method.getParameterCount() != 0) {
                methodErrors.add(method);
            }
            if (!method.getReturnType().equals(void.class)) {
                methodErrors.add(method);
            }
        }
        return methodErrors;
    }

    private void injectFields() {
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

    private void hasCyclicDependency() {
        for (Dependency dep : dependencies) {
            for (Dependency dep1 : dependencies) {
                if (dep.getDependencies().contains(dep1.getClazz())) {
                    throw new RuntimeException("Class " + dep.getClazz().getName() + " depends on " + dep1.getClazz().getName() + " but " + dep1.getClazz().getName() + " also depends on " + dep.getClazz().getName());
                }
            }
        }
    }

    private void generateLoadOrder() {
        //sort the dependencies by the classes they need in correct order
        dependencies.sort(dependenciesLoadOrder);

        AtomicInteger index = new AtomicInteger();
        for (Dependency dependency : dependencies) {
            System.out.println(index.getAndIncrement() + ": " + dependency.getClazz().getName() + " -> " + dependency.getDependencies().size() + " dependencies");
        }
    }

    private void createInstances() {
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
