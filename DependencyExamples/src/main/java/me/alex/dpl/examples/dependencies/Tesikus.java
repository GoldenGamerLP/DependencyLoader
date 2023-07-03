package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.AutoRun;
import me.alex.dpl.annotation.DependencyConstructor;

@AutoLoadable
public class Tesikus {

    @DependencyConstructor
    public Tesikus() {
    }

    @AutoRun(priority = 9765)
    private void lalal() {
        System.out.println("Hey mein Name ist alex, und deiner?");
    }
}
