package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.DependencyConstructor;

@AutoLoadable()
public class Test {

    @DependencyConstructor
    public Test(Nah nah) {
    }

    public void test() {
        System.out.println("Test213");
    }
}
