package me.alex.dpl.examples;

import me.alex.dpl.DependencyManager;
import me.alex.dpl.examples.dependencies.Wichtig;

public class Main {
    public static void main(String[] args) {
        DependencyManager dep = DependencyManager.getDependencyManager();

        //Add a standalone dependency to the dependency manager
        dep.addDependency(Wichtig.class, new Wichtig());

        //Initialize the dependency manager and all dependencies
        dep.init(true);
    }
}
