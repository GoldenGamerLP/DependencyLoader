package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable()
public class Test6 {

    @DependencyConstructor
    public Test6(Test5 test) {
    }
}
