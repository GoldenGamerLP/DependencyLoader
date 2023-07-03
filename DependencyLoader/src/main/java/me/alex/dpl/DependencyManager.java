package me.alex.dpl;

import me.alex.dpl.annotation.AutoRun;
import me.alex.dpl.annotation.DependencyConstructor;
import me.alex.dpl.annotation.Inject;
import me.alex.dpl.pojo.Dependency;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DependencyManager {

    private static DependencyManager dependencyManager;
    private final static String ANNOTATIONS = "META-INF/annotations";
    private final Map<Class<?>, Object> objectCache = new ConcurrentHashMap<>();
    private final Logger log = Logger.getLogger(this.getClass().getSimpleName());
    private final AtomicBoolean init = new AtomicBoolean(false);

    private DependencyManager() {
    }

    //Singleton
    public synchronized static DependencyManager getDependencyManager() {
        if (dependencyManager == null) {
            dependencyManager = new DependencyManager();
        }
        return dependencyManager;
    }

    public synchronized void init() {
        if (init.compareAndExchange(false, true)) {
            log.log(Level.SEVERE, "DependencyManager#init can only be called once.");
        }
        //Read meta-inf file
        Instant now = Instant.now();
        //The classes are already in the right order from the Annotation processor
        List<Class<?>> indexedClasses = readClasses(this.getClass().getClassLoader());
        List<Dependency> fetchedClasses = fetchClasses(indexedClasses);

        createInstances(fetchedClasses);
        injectFields(fetchedClasses);
        runMethods(fetchedClasses);

        log.info("Success! Finished loading in " + (Instant.now().toEpochMilli() - now.toEpochMilli()) + "ms. With " + fetchedClasses.size() + " classes.");
    }

    public void addDependency(Object obj) {
        objectCache.putIfAbsent(obj.getClass(), obj);
    }

    private List<Dependency> fetchClasses(List<Class<?>> classesToIndex) {
        return classesToIndex.stream()
                .parallel()
                .map(this::processClass)
                .toList();
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
        Set<String> foundEntries = new LinkedHashSet<>();
        InputStream stream = getClass().getClassLoader().getResourceAsStream(this.ANNOTATIONS);
        if (stream == null) {
            log.info("Failed to find indexed class file.");
            return List.of();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            while (reader.ready()) {
                String line = reader.readLine();
                foundEntries.add(line);
            }
        } catch (IOException e) {
            log.info("Error while reading indexed class file.");
        }

        List<Class<?>> classes = new LinkedList<>();
        for (String foundEntry : foundEntries) {
            try {
                classes.add(loader.loadClass(foundEntry));
            } catch (ClassNotFoundException e) {
                log.info("Did not find the class: " + foundEntry + ". Did you change something at compiletime?");
            }
        }
        return classes;
    }

    private void runMethods(List<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            Object object = objectCache.get(dependency.getClazz());
            if (object == null) {
                throw new RuntimeException("Failed to run methods in class " + dependency.getClazz().getName() + " because the object is null");
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
            throw new RuntimeException("Failed to run method " + method.getName() + " in class " + method.getClass());
        }
    }

    private void createInstances(List<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            if (objectCache.containsKey(dependency.getClazz())) {
                System.out.println("Class " + dependency.getClazz().getName() + " already created");
                continue;
            }

            Object[] parameters = new Object[dependency.getConstructorParameters().size()];
            for (int i = 0; i < parameters.length; i++) {
                Class<?> depClass = dependency.getConstructorParameters().get(i);
                Object para = objectCache.get(depClass);
                if (para == null) {
                    throw new RuntimeException("Dependency " + depClass.getName() + " not found");
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
            throw new RuntimeException("Error while creating instance of class " + dependency.getClazz().getName(), e);
        }
    }

    private Dependency processClass(Class<?> klass) {
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
            throw new IllegalArgumentException("Class " + klass.getName() + " has invalid parameters: " + errors);
        }
        if (!fieldErrors.isEmpty()) {
            throw new IllegalArgumentException("Class " + klass.getName() + " has invalid fields: " + fieldErrors);
        }
        if (!methodErrors.isEmpty()) {
            throw new IllegalArgumentException("Class " + klass.getName() + " has invalid methods: " + methodErrors);
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
                    throw new RuntimeException("Class " + clazz.getName() + " has a circular dependency");
                }
            }
        }
        return dependenciesError;
    }
}
