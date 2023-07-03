package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.DependencyConstructor;

@AutoLoadable
public class Test3 {

    @DependencyConstructor
    public Test3(Test2 test) {
    }
}
