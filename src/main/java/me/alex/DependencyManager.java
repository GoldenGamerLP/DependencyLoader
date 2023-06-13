package me.alex;

import org.atteo.classindex.ClassIndex;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DependencyManager {

    public static void main(String[] args) {
        new DependencyManager();
    }

    private final Comparator<Dependency> dependenciesLoadOrder = (o1, o2) -> {
        List<Class<?>> o1Dependencies = new ArrayList<>(o1.getDependencies());
        List<Class<?>> o2Dependencies = new ArrayList<>(o2.getDependencies());
        //remove all dependencies that both classes have
        o1Dependencies.retainAll(o2Dependencies);
        o2Dependencies.retainAll(o1Dependencies);

        //if both classes have no dependencies, then they are equal. If one class depends on the other, then the class with the dependency is greater
        return o1.getDependencies().contains(o2.getClazz()) ? 1 : o2.getDependencies().contains(o1.getClazz()) ? -1 : 0;
    };

    private final List<Dependency> dependencies;
    private final List<Object> createdObjects;
    public DependencyManager() {
        this.dependencies = new ArrayList<>();
        this.createdObjects = new ArrayList<>();
        Iterable<Class<?>> classes = ClassIndex.getAnnotated(InjectDependency.class);

        //get all classes and check the constructor for the createdObjectMap, if has no createdObjectMap, then create a new instance
        //if has createdObjectMap, then check if the createdObjectMap are already created, if not, then create them, then create the instance
        classes.forEach(klass -> {
            //get a constructor with the annotation DependencyConstructor
            Optional<Constructor<?>> constructor = Arrays.stream(klass.getConstructors())
                    .filter(constructor1 -> constructor1.isAnnotationPresent(DependencyConstructor.class))
                    .findFirst();
            if(constructor.isEmpty()) {
                throw new RuntimeException("No constructor with annotation DependencyConstructor found in class " + klass.getName());
            }

            //get the parameters of the constructor and put them into a list
            List<Class<?>> parameterTypes = new ArrayList<>();
            Collections.addAll(parameterTypes, constructor.get().getParameterTypes());
            List<Class<?>> errors = checkParameters(klass, parameterTypes);
            if(!errors.isEmpty()) {
                throw new RuntimeException("Class " + klass.getName() + " has invalid parameters: " + errors);
            }
            dependencies.add(new Dependency(klass, parameterTypes));
        });
        hasCyclicDependency();
        generateLoadOrder();
        createInstances();
    }

    private List<Class<?>> checkParameters(Class<?> clazz, List<Class<?>> parameterTypes) {
        //check parameters for @InjectDependency and for any constructor that has @DependencyConstructor
        List<Class<?>> dependenciesError = new ArrayList<>();
        for(Class<?> parameterType : parameterTypes) {
            System.out.println("Checking parameter " + parameterType.getName() + " for class " + clazz.getName());
            boolean hasDependencyConstructor = Arrays.stream(parameterType.getConstructors())
                    .anyMatch(constructor -> constructor.isAnnotationPresent(DependencyConstructor.class));
            boolean hasInjectDependency = parameterType.isAnnotationPresent(InjectDependency.class);
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
            
        }
    }

    private Object createInstance(Class<?> klass, Object... parameters) {
        try {
            return klass.getDeclaredConstructor().newInstance(parameters);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("Error while creating instance of class " + klass.getName(), e);
        }
    }
}
