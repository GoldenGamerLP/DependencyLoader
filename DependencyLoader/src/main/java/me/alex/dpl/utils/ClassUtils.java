package me.alex.dpl.utils;

public class ClassUtils {

    public static String getCurrentClass() {
        return Thread.currentThread().getStackTrace()[2].getClassName();
    }

    public static String getCurrentPackageName() {
        String currentClass = getCurrentClass();
        int lastDot = currentClass.lastIndexOf(".");
        return currentClass.substring(0, lastDot);
    }
}
