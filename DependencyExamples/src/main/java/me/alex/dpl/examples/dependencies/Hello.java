package me.alex.dpl.examples.dependencies;


import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.AutoRun;
import me.alex.dpl.annotation.DependencyConstructor;
import me.alex.dpl.annotation.Inject;

@AutoLoadable
public class Hello {

    @Inject
    private WasGeht wasGeht;

    @DependencyConstructor
    public Hello(WasGeht wasGeht, Nah nah) {
        System.out.println("Hello, World!!!!!!!");
        System.out.println(wasGeht.getWasGeht());
    }

    @AutoRun(priority = 3)
    private void test() {
        System.out.println(wasGeht.getWasGeht() + " from test");
    }

}
