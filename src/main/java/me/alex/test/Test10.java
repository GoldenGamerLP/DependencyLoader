package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.AutoRun;
import me.alex.annotation.DependencyConstructor;

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
