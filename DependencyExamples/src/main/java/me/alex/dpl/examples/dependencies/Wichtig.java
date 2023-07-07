package me.alex.dpl.examples.dependencies;

import me.alex.dpl.annotation.AutoRun;

public class Wichtig {

    public Wichtig() {
    }

    public String getWichtig() {
        return "Wichtig";
    }

    @AutoRun
    public void test() {
        System.out.println("Test123132132123312312132");
    }
}
