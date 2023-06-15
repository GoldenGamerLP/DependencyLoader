package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable()
public class Test4 {

    @DependencyConstructor
    public Test4(Test3 test) {
    }
}
