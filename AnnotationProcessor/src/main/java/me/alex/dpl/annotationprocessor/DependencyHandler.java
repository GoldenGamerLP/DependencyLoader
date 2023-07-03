package me.alex.dpl.annotationprocessor;

import java.util.*;

public class DependencyHandler {

    public DependencyHandler() {
    }

    public List<Dependency> sortDependencies(List<Dependency> dependencies) {
        List<Dependency> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        Stack<Dependency> stack = new Stack<>();
        for (Dependency dependency : dependencies) {
            if (!visited.contains(dependency.getKlass())) {
                stack.push(dependency);
                while (!stack.isEmpty()) {
                    Dependency current = stack.peek();
                    visited.add(current.getKlass());

                    boolean allDependenciesVisited = true;
                    for (String clazz : current.getDependencies()) {
                        Dependency dep = findDependency(dependencies, clazz);
                        if (dep == null) continue;
                        if (dep.getDependencies().contains(current.getKlass())) {
                            throw new IllegalArgumentException("Cyclic dependency found between " + current.getKlass() + " and " + dep.getKlass());
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

    private Dependency findDependency(List<Dependency> dependencies, String clazz) {
        for (Dependency dependency : dependencies) {
            if (dependency.getKlass().equals(clazz)) {
                return dependency;
            }
        }
        return null;
    }

    public static class Dependency {
        private final String klass;
        private final List<String> dependencies;


        public Dependency(String className, List<String> dependencies) {
            this.klass = className;

            this.dependencies = dependencies;
        }

        public String getKlass() {
            return klass;
        }

        public List<String> getDependencies() {
            return dependencies;
        }
    }
}
