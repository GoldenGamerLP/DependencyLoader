package me.alex;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Dependency {
    private final Class<?> clazz;
    private final List<Class<?>> dependencies;
    private final Constructor<?> constructor;
    private final List<Field> injectionFields;
    private final List<Method> injectionMethods;

    public Dependency(Class<?> clazz, Constructor<?> cons, List<Class<?>> dependencies, List<Field> injectionFields, List<Method> injectionMethods) {
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
        return injectionFields;
    }

    public List<Class<?>> getDependencies() {
        return dependencies;
    }

    public Constructor<?> getConstructor() {
        return constructor;
    }

    public List<Method> getInjectionMethods() {
        return injectionMethods;
    }


    public List<Class<?>> neededClasses() {
        //all classes needed for this class to be created, excluding the class itself
        List<? extends Class<?>> fields = injectionFields.stream().map(Field::getType).toList();
        List<? extends Class<?>> cons = List.of(constructor.getParameterTypes());
        List<Class<?>> all = new ArrayList<>();
        all.addAll(fields);
        all.addAll(cons);
        return all;
    }
}
