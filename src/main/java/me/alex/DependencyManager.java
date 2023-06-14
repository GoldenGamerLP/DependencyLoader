package me.alex;

import org.atteo.classindex.ClassIndex;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DependencyManager {

    public static void main(String[] args) {
        new DependencyManager();
    }

    private final Comparator<Dependency> dependenciesLoadOrder = (o1, o2) -> {
        if(o1.getDependencies().contains(o2.getClazz()) && o2.getDependencies().contains(o1.getClazz())) {
            throw new RuntimeException("Cyclic dependency between " + o1.getClazz().getName() + " and " + o2.getClazz().getName());
        }

        if(o1.getDependencies().contains(o2.getClazz())) {
            return 1;
        } else if(o2.getDependencies().contains(o1.getClazz())) {
            return -1;
        } else {
            return 0;
        }
    };

    private final List<Dependency> dependencies;
    private final Map<Class<?>,Object> createdObjects;
    public DependencyManager() {
        this.dependencies = new ArrayList<>();
        this.createdObjects = new HashMap<>();
        Iterable<Class<?>> classes = ClassIndex.getAnnotated(AutoLoadable.class);

        //get all classes and check the constructor for the createdObjectMap, if has no createdObjectMap, then create a new instance
        //if has createdObjectMap, then check if the createdObjectMap are already created, if not, then create them, then create the instance
        classes.forEach(klass -> {
            List<Constructor<?>> constructor = Arrays.stream(klass.getConstructors())
                    .filter(constructor1 -> constructor1.isAnnotationPresent(DependencyConstructor.class))
                    .toList();

            if(constructor.isEmpty()) {
                throw new RuntimeException("No constructor with annotation DependencyConstructor found in class " + klass.getName());
            }
            if(constructor.size() > 1) {
                throw new RuntimeException("Multiple constructors with annotation DependencyConstructor found in class " + klass.getName());
            }

            List<Field> fields = Arrays.stream(klass.getFields())
                    .filter(field -> field.isAnnotationPresent(AutoWired.class))
                    .toList();
            Constructor<?> cons = constructor.get(0);
            List<Class<?>> parameterTypes = List.of(cons.getParameterTypes());
            List<Method> methods = new ArrayList<>(Arrays.stream(klass.getMethods())
                    .filter(method -> method.isAnnotationPresent(AutoRun.class))
                    .toList());
            System.out.println(Arrays.stream(klass.getMethods()).map(method -> Arrays.stream(method.getAnnotations()).map(Annotation::getClass)).toList());
            List<Method> methodErrors = checkMethods(klass, methods);
            List<Class<?>> errors = checkParameters(klass, parameterTypes);
            List<Field> fieldErrors = checkFields(klass, fields);

            if(!errors.isEmpty()) {
                throw new RuntimeException("Class " + klass.getName() + " has invalid parameters: " + errors);
            }
            if(!fieldErrors.isEmpty()) {
                throw new RuntimeException("Class " + klass.getName() + " has invalid fields: " + fieldErrors);
            }
            if(!methodErrors.isEmpty()) {
                throw new RuntimeException("Class " + klass.getName() + " has invalid methods: " + methodErrors);
            }
            //sort the methods by priority
            methods.sort(Comparator.comparingInt(method -> method.getAnnotation(AutoRun.class).priority()));

            dependencies.add(new Dependency(klass, cons, parameterTypes, fields, methods));
        });

        hasCyclicDependency();
        generateLoadOrder();
        printClassesAndInfo();
        createInstances();
        injectFields();
        runMethods();
    }

    private void printClassesAndInfo() {
        StringBuilder builder = new StringBuilder("Classes to load:\n");
        for (int i = 0; i < dependencies.size(); i++) {
            Dependency dependency = dependencies.get(i);
            builder.append(i + 1).append(". ").append(dependency.getClazz().getName())
                    .append("\n")
                    .append("   Constructor: ").append(dependency.getConstructor().getName())
                    .append("\n")
                    .append("   Dependencies: ").append(dependency.getDependencies())
                    .append("\n")
                    .append("   Injection fields: ").append(dependency.getInjectionFields())
                    .append("\n")
                    .append("   Injection methods: ").append(dependency.getInjectionMethods())
                    .append("\n");
        }
        System.out.println(builder.toString());
    }

    private void runMethods() {
        for(Dependency dependency : dependencies) {
            Object object = createdObjects.get(dependency.getClazz());
            for(Method method : dependency.getInjectionMethods()) {
                try {
                    method.invoke(object);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException("Failed to run method " + method.getName() + " in class " + dependency.getClazz().getName());
                }
            }
        }
    }

    private List<Method> checkMethods(Class<?> klass, List<Method> methods) {
        List<Method> methodErrors = new ArrayList<>();
        for(Method method : methods) {
            if(method.getParameterCount() != 0) {
                methodErrors.add(method);
            }
            if(!method.getReturnType().equals(void.class)) {
                methodErrors.add(method);
            }
        }
        return methodErrors;
    }

    private void injectFields() {
        for(Dependency dependency : dependencies) {
            Object object = createdObjects.get(dependency.getClazz());
            for(Field field : dependency.getInjectionFields()) {
                try {
                    field.set(object, createdObjects.get(field.getType()));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to inject field " + field.getName() + " in class " + dependency.getClazz().getName());
                }
            }
        }
    }

    private List<Field> checkFields(Class<?> klass, List<Field> fields) {
        List<Field> fieldErrors = new ArrayList<>();
        for(Field field : fields) {
            if(field.getType().equals(klass)) {
                fieldErrors.add(field);
            }
        }
        return fieldErrors;
    }

    private List<Class<?>> checkParameters(Class<?> clazz, List<Class<?>> parameterTypes) {
        //check parameters for @AutoLoadable and for any constructor that has @DependencyConstructor
        List<Class<?>> dependenciesError = new ArrayList<>();
        for(Class<?> parameterType : parameterTypes) {
            System.out.println("Checking parameter " + parameterType.getName() + " for class " + clazz.getName());
            boolean hasDependencyConstructor = Arrays.stream(parameterType.getConstructors())
                    .anyMatch(constructor -> constructor.isAnnotationPresent(DependencyConstructor.class));
            boolean hasInjectDependency = parameterType.isAnnotationPresent(AutoLoadable.class);
            if(!hasDependencyConstructor && !hasInjectDependency) {
                dependenciesError.add(parameterType);
            } else {
                //check if parametertype has clazz as parameter
                if(parameterType.equals(clazz)) {
                    throw new RuntimeException("Class " + clazz.getName() + " has a circular dependency");
                }
            }
        }
        return dependenciesError;
    }

    private void hasCyclicDependency() {
        for(Dependency dependency : dependencies) {
            for(Class<?> dependencyClass : dependency.getDependencies()) {
                for(Dependency dependency1 : dependencies) {
                    if(dependency1.getClazz().equals(dependencyClass)) {
                        if(dependency1.getDependencies().contains(dependency.getClazz())) {
                            //cyclic dependency and show which classes
                            throw new RuntimeException("Cyclic dependency between " + dependency.getClazz().getName() + " and " + dependency1.getClazz().getName());
                        }
                    }
                }
            }
        }
    }

    private void generateLoadOrder() {
        //sort the dependencies by the classes they need in correct order
        dependencies.sort(dependenciesLoadOrder);

        AtomicInteger index = new AtomicInteger();
        for(Dependency dependency : dependencies) {
            System.out.println(index.getAndIncrement() + ": " + dependency.getClazz().getName() + " -> " + dependency.getDependencies().size() + " dependencies");
        }
    }

    private void createInstances() {
        for(Dependency dependency : dependencies) {
            if(createdObjects.containsKey(dependency.getClazz())) {
                System.out.println("Class " + dependency.getClazz().getName() + " already created");
                continue;
            }
            Object[] parameters = new Object[dependency.getDependencies().size()];
            for(int i = 0; i < parameters.length; i++) {
                Class<?> depClass = dependency.getDependencies().get(i);
                Object para = createdObjects.get(depClass);
                if(para == null) {
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
