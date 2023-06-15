package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable()
public class Test3 {

    @DependencyConstructor
    public Test3(Test2 test) {
    }
}
