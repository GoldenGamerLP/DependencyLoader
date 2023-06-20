package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.DependencyConstructor;

@AutoLoadable()
public class Test9 {
    @DependencyConstructor
    public Test9(Test8 test) {
    }
}
