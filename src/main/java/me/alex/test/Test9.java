package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable()
public class Test9 {
    @DependencyConstructor
    public Test9(Test8 test) {
    }
}
