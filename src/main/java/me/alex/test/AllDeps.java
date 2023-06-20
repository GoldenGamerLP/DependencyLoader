package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.AutoRun;
import me.alex.annotation.DependencyConstructor;
import me.alex.annotation.Inject;

@AutoLoadable
public class AllDeps {

    @Inject
    private Test10 test10;
    @Inject
    private Test2 test2;
    @Inject
    private Test9 test9;
    @Inject
    private Wichtig wichtig;

    @DependencyConstructor
    public AllDeps() {
    }

    @AutoRun(priority = 1)
    private void foo() {
        System.out.println(wichtig.getWichtig());
        System.out.println(test10 != null);
    }
}
