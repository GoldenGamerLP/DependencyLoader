package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.DependencyConstructor;

@AutoLoadable()
public class Test6 {

    @DependencyConstructor
    public Test6(Test5 test) {
    }
}
