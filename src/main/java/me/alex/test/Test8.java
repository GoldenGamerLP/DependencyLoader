package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable()
public class Test8 {

    @DependencyConstructor
    public Test8(Test7 test) {
    }
}
