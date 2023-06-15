package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable()
public class Test2 {

    @DependencyConstructor
    public Test2(Test nah) {
    }
}
