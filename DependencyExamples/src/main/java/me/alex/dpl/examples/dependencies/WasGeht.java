package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.DependencyConstructor;

@AutoLoadable()
public class WasGeht {

    @DependencyConstructor
    public WasGeht(Nah nah) {
        System.out.println("Was geht?");
    }

    public String getWasGeht() {
        return "Was geht?";
    }
}
