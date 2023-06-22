package me.alex.dpl.utils;

public class ClassUtils {

    public static Class<?> getCurrentClass() {
        String callerName = Thread.currentThread().getStackTrace()[2].getClassName();
        Class<?> clazz = null;

        try {
            clazz = Class.forName(callerName);
            // Do something with it ...
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return clazz;
    }
}
