package me.alex.dpl.examples;


import me.alex.dpl.DependencyManager;
import me.alex.dpl.examples.dependencies.Wichtig;

import java.time.Duration;
import java.time.Instant;

public class TestClazz {

    public static void main(String[] args) {
        Instant now = Instant.now();
        DependencyManager dependencyManager = DependencyManager.getDependencyManager();
        dependencyManager.addDependency(new Wichtig());

        dependencyManager.init();

        System.out.println("Time: " + Duration.between(now, Instant.now()).toMillis() + "ms");
    }
}
