package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable()
public class Test7 {

    @DependencyConstructor
    public Test7(Test6 test) {
    }
}
