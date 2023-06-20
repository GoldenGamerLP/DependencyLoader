package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.DependencyConstructor;

@AutoLoadable()
public class Test2 {

    @DependencyConstructor
    public Test2(Test nah) {
    }
}
