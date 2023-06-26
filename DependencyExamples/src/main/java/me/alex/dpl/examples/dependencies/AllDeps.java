package me.alex.dpl.examples.dependencies;


import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.AutoRun;
import me.alex.dpl.annotation.DependencyConstructor;
import me.alex.dpl.annotation.Inject;

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

    @AutoRun(priority = 2, async = true)
    private void bar() {
        System.out.println(test2 != null);
        System.out.println(test9 != null);
        System.out.println(" dasdadsa --->" + Thread.currentThread().getName());
    }
}
