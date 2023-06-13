package me.alex.test;

import me.alex.DependencyConstructor;
import me.alex.InjectDependency;

@InjectDependency
public class WasGeht {

    @DependencyConstructor
    public WasGeht(Nah nah) {
        System.out.println("Was geht?");
    }

    public String getWasGeht() {
        return "Was geht?";
    }
}
