package me.alex.dpl.pojo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class Dependency {
    private final Class<?> clazz;
    private final List<Class<?>> dependencies;
    private final Constructor<?> constructor;
    private final List<Field> injectionFields;
    private final List<AutoRunMethod> injectionMethods;

    public Dependency(Class<?> clazz, Constructor<?> cons, List<Class<?>> dependencies, List<Field> injectionFields, List<AutoRunMethod> injectionMethods) {
        this.clazz = clazz;
        this.dependencies = dependencies;
        this.constructor = cons;
        this.injectionFields = injectionFields;
        this.injectionMethods = injectionMethods;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public List<Field> getInjectionFields() {
        return List.copyOf(injectionFields);
    }


    public Constructor<?> getConstructor() {
        return constructor;
    }

    public List<AutoRunMethod> getInjectionMethods() {
        return List.copyOf(injectionMethods);
    }

    public List<Class<?>> getConstructorParameters() {
        return List.of(constructor.getParameterTypes());
    }

    public List<Class<?>> getDependencies() {
        //all classes needed for this class to be created, excluding the class itself
        List<? extends Class<?>> fields = injectionFields.stream().map(Field::getType).toList();
        List<? extends Class<?>> cons = List.of(constructor.getParameterTypes());
        List<Class<?>> all = new ArrayList<>();
        all.addAll(fields);
        all.addAll(cons);
        return all;
    }

    public static class AutoRunMethod implements Comparable<AutoRunMethod> {
        private final Method method;
        private final int priority;
        private final boolean async;

        public AutoRunMethod(Method method, int priority, boolean async) {
            this.method = method;
            this.priority = priority;
            this.async = async;
        }

        public Method getMethod() {
            return method;
        }

        public int getPriority() {
            return priority;
        }

        public boolean isAsync() {
            return async;
        }

        @Override
        public int compareTo(Dependency.AutoRunMethod o) {
            return Integer.compare(priority, o.priority);
        }
    }
}
