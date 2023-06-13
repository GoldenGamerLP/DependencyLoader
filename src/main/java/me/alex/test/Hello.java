package me.alex.test;

import me.alex.DependencyConstructor;
import me.alex.InjectDependency;

@InjectDependency
public class Hello {

    @DependencyConstructor
    public Hello(WasGeht wasGeht, Nah nah) {
        System.out.println("Hello, World!!!!!!!");
        System.out.println(wasGeht.getWasGeht());
    }
}
