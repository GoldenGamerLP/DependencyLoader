package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable()
public class Test5 {

    @DependencyConstructor
    public Test5(Test4 test) {
    }
}
