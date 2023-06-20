package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.AutoRun;
import me.alex.dpl.annotation.DependencyConstructor;

@AutoLoadable()
public class Test10 {
    @DependencyConstructor
    public Test10(Test9 test) {
    }


    @AutoRun
    private void foo() {
        System.out.println("Works!!!");
    }
}
