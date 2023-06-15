package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.AutoRun;
import me.alex.annotation.DependencyConstructor;
import me.alex.annotation.Inject;

@AutoLoadable
public class Hello {

    @Inject
    private WasGeht wasGeht;
    @Inject
    private Wichtig wichtig;

    @DependencyConstructor
    public Hello(WasGeht wasGeht, Nah nah) {
        System.out.println("Hello, World!!!!!!!");
        System.out.println(wasGeht.getWasGeht());
    }

    @AutoRun(priority = 3)
    private void test() {
        System.out.println(wasGeht.getWasGeht() + " from test");
    }

    @AutoRun(priority = 2)
    private void test2() {
        System.out.println(wichtig.getWichtig() + " from test2");
    }
}
