package me.alex;

import java.util.List;

public class Dependency {
    private final Class<?> clazz;
    private final List<Class<?>> dependencies;

    public Dependency(Class<?> clazz, List<Class<?>> dependencies) {
        this.clazz = clazz;
        this.dependencies = dependencies;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public List<Class<?>> getDependencies() {
        return dependencies;
    }
}
