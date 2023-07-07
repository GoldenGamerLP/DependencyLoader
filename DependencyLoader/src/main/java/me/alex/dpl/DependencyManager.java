package me.alex.dpl;

import me.alex.dpl.annotation.AutoRun;
import me.alex.dpl.annotation.DependencyConstructor;
import me.alex.dpl.annotation.Inject;
import me.alex.dpl.pojo.Dependency;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The DependencyManager is the main class of this library. It is responsible for loading all dependencies and injecting them into each other.
 * <p>
 * The DependencyManager is a singleton and can be accessed via {@link #getDependencyManager()}.
 *
 * @author Alexander W / GoldenGamer
 * @version 1.0
 * @serial 1L
 */
public class DependencyManager {
    private static DependencyManager dependencyManager;
    private final Map<Class<?>, Object> objectCache = new ConcurrentHashMap<>();
    private final Logger log = Logger.getLogger(this.getClass().getSimpleName());
    private final AtomicBoolean init = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newWorkStealingPool(ForkJoinPool.getCommonPoolParallelism());

    //Non-Instantiable
    private DependencyManager() {
    }

    /**
     * Returns the DependencyManager instance
     *
     * @return {@link DependencyManager}
     */
    public synchronized static DependencyManager getDependencyManager() {
        if (dependencyManager == null) {
            dependencyManager = new DependencyManager();
        }
        return dependencyManager;
    }

    /**
     * Initializes the DependencyManager and loads all dependencies. This method can only be called once.
     */
    public synchronized void init() {
        if (init.compareAndExchange(false, true)) {
            log.log(Level.SEVERE, "DependencyManager#init can only be called once.");
        }

        //Start of loading
        Instant now = Instant.now();
        //The classes are already in the right order from the Annotation processor
        List<Class<?>> indexedClasses = readClasses(this.getClass().getClassLoader());
        List<Dependency> fetchedClasses = fetchClasses(indexedClasses);

        createInstances(fetchedClasses);
        injectFields(fetchedClasses);
        runMethods(fetchedClasses);

        //End of loading
        executorService.shutdown();

        try {
            boolean t = executorService.awaitTermination(1, TimeUnit.SECONDS);
            if (!t) {
                log.severe("Failed to await termination of executor service.");
            }
        } catch (InterruptedException e) {
            log.severe("Failed to await termination of executor service.");
        }

        log.info("Success! Finished loading in " + (Instant.now().toEpochMilli() - now.toEpochMilli()) + "ms. With " + fetchedClasses.size() + " classes.");
    }

    /**
     * Adds a standalone class to the DependencyManager.
     *
     * @param obj {@link Object} Any object
     */
    public void addDependency(Object obj) {
        objectCache.putIfAbsent(obj.getClass(), obj);
    }

    /**
     * Gets a class from the {@link DependencyManager} cache. Returns null if the class is not found.
     *
     * @param clazz The class to get
     * @param <T>   The class type
     * @return {@link T} The class
     */
    @Nullable
    public <T> T getDependency(Class<T> clazz) {
        Object obj = objectCache.get(clazz);

        if (obj == null) {
            return null;
        }

        return clazz.cast(obj);
    }

    private List<Dependency> fetchClasses(List<Class<?>> classesToIndex) {
        List<Dependency> dependencies = new ArrayList<>();
        List<Future<Dependency>> futures = classesToIndex.stream().map(aClass -> executorService.submit(() -> processClass(aClass))).toList();

        for (Future<Dependency> future : futures) {
            try {
                dependencies.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                log.severe("Failed to fetch class.");
            }
        }

        return dependencies;
    }

    private void injectFields(List<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            Object object = objectCache.get(dependency.getClazz());
            for (Field field : dependency.getInjectionFields()) {
                Object fieldObj = objectCache.get(field.getType());
                if (fieldObj == null) {
                    log.info("Failed to inject field" + field.getName() + " in class " + dependency.getClazz().getName() + ". Forgot to add a Dependency?");
                    return;
                }
                try {
                    field.setAccessible(true);
                    field.set(object, fieldObj);
                } catch (IllegalAccessException e) {
                    log.info("Failed to inject field " + field.getName() + " in class " + dependency.getClazz().getName());
                    return;
                }
            }
        }
    }

    public List<Class<?>> readClasses(ClassLoader loader) {
        List<String> foundEntries = new ArrayList<>();
        InputStream stream = getClass().getClassLoader().getResourceAsStream(Constants.ANNOTATION_STORAGE_FILE);
        if (stream == null) {
            log.info("Failed to find indexed class file.");
            return List.of();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8), 8192 / 2)) {
            while (reader.ready()) {
                String line = reader.readLine();
                foundEntries.add(line);
            }
        } catch (IOException e) {
            log.info("Error while reading indexed class file.");
        }

        List<Class<?>> classes = new ArrayList<>();
        List<Future<Class<?>>> futures = new ArrayList<>();

        for (String entry : foundEntries) {
            futures.add(executorService.submit(() -> {
                try {
                    return loader.loadClass(entry);
                } catch (ClassNotFoundException e) {
                    log.info("Failed to find class " + entry);
                    return null;
                }
            }));
        }

        for (Future<Class<?>> future : futures) {
            try {
                Class<?> clazz = future.get();

                if (clazz == null) {
                    continue;
                }

                classes.add(clazz);
            } catch (InterruptedException | ExecutionException e) {
                log.severe("Failed to get class from future.");
            }
        }


        return classes;
    }

    private void runMethods(List<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            Object object = objectCache.get(dependency.getClazz());
            if (object == null) {
                log.severe("Failed to run methods in class " + dependency.getClazz().getName() + " because the object is null");
            }
            for (Dependency.AutoRunMethod autoRunMethod : dependency.getInjectionMethods()) {
                if (autoRunMethod.isAsync()) {
                    CompletableFuture.runAsync(() -> runMethod(autoRunMethod, object));
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
            log.severe("Failed to run method " + method.getName() + " in class " + object.getClass().getName());
        }
    }

    private void createInstances(List<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            if (objectCache.containsKey(dependency.getClazz())) {
                log.warning("Dependency " + dependency.getClazz().getName() + " already exists in cache. Skipping.");
                continue;
            }

            Object[] parameters = new Object[dependency.getConstructorParameters().size()];
            for (int i = 0; i < parameters.length; i++) {
                Class<?> depClass = dependency.getConstructorParameters().get(i);
                Object para = objectCache.get(depClass);
                if (para == null) {
                    log.severe("Failed to find dependency " + depClass.getName() + " for class " + dependency.getClazz().getName());
                    return;
                }
                parameters[i] = para;
            }
            Object instance = createInstance(dependency, parameters);
            objectCache.put(dependency.getClazz(), instance);
        }
    }

    private Object createInstance(Dependency dependency, Object... parameters) {
        try {
            return dependency.getConstructor().newInstance(parameters);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            log.severe("Failed to create instance of class " + dependency.getClazz().getName());
        }
        return null;
    }

    private Dependency processClass(Class<?> klass) {
        List<Constructor<?>> constructor = Arrays.stream(klass.getConstructors())
                .filter(constructor1 -> constructor1.isAnnotationPresent(DependencyConstructor.class))
                .toList();
        if (constructor.size() != 1) {
            log.severe("Class " + klass.getName() + " has " + constructor.size() + " constructors with the DependencyConstructor annotation. There should only be one.");
            return null;
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
            log.severe("Class " + klass.getName() + " has invalid dependencies: " + errors);
        }
        if (!fieldErrors.isEmpty()) {
            log.severe("Class " + klass.getName() + " has invalid fields: " + fieldErrors);
        }
        if (!methodErrors.isEmpty()) {
            log.severe("Class " + klass.getName() + " has invalid methods: " + methodErrors);
        }

        //sort the methods by priority
        Collections.sort(methods);

        //force add the class to the load order
        return new Dependency(klass, cons, parameterTypes, fields, methods);
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
            if (objectCache.containsKey(parameterType)) {
                continue;
            }

            boolean hasDependencyConstructor = Arrays.stream(parameterType.getConstructors())
                    .anyMatch(constructor -> constructor.isAnnotationPresent(DependencyConstructor.class));
            if (!hasDependencyConstructor) {
                dependenciesError.add(parameterType);
            } else {
                //check if parametertype has clazz as parameter
                if (parameterType.equals(clazz)) {
                    log.severe("Class " + clazz.getName() + " has a circular dependency with class " + parameterType.getName());
                }
            }
        }
        return dependenciesError;
    }
}
