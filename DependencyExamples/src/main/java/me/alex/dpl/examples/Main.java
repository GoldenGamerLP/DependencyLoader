package me.alex.dpl.examples;

import me.alex.dpl.DependencyManager;
import me.alex.dpl.examples.dependencies.Wichtig;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

public class Main {

    private final String path = "META-INF/annotations";
    @Test
    public void testDependencyLoader() {
        //Read meta-inf file

        for(Class<?> clazz : readClasses()) {
            System.out.println(clazz);
        }
    }

    public List<Class<?>> readClasses() {
        ClassLoader classLoader = getClass().getClassLoader();
        Set<String> foundEntries = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(path)))) {
            while (reader.ready()) {
                String line = reader.readLine();
                foundEntries.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<Class<?>> classes = new LinkedList<>();
        for (String foundEntry : foundEntries) {
            try {
                classes.add(classLoader.loadClass(foundEntry));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return classes;
    }
}
