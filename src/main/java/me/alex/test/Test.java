package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable()
public class Test {

    @DependencyConstructor
    public Test(Nah nah) {
    }

    public void test() {
        System.out.println("Test213");
    }
}
