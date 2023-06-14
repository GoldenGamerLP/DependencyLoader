package me.alex.test;

import me.alex.AutoRun;
import me.alex.DependencyConstructor;
import me.alex.AutoLoadable;

@AutoLoadable
public class Hello {

    @DependencyConstructor
    public Hello(WasGeht wasGeht, Nah nah) {
        System.out.println("Hello, World!!!!!!!");
        System.out.println(wasGeht.getWasGeht());
    }

    @AutoRun(priority = 1)
    public void test() {
        System.out.println("test");
    }
}
