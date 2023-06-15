package me.alex.test;

import me.alex.annotation.AutoLoadable;
import me.alex.annotation.DependencyConstructor;

@AutoLoadable
public class WasGeht {

    @DependencyConstructor
    public WasGeht(Nah nah) {
        System.out.println("Was geht?");
    }

    public String getWasGeht() {
        return "Was geht?";
    }
}
