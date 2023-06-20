package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.DependencyConstructor;

@AutoLoadable()
public class Test4 {

    @DependencyConstructor
    public Test4(Test3 test) {
    }
}
