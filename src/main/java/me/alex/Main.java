package me.alex;

import me.alex.test.Wichtig;

public class Main {
    public static void main(String[] args) {
        DependencyManager dep = DependencyManager.getDependencyManager();

        dep.addDependency(Wichtig.class,new Wichtig());
        dep.init();
    }
}
