package me.alex.dpl.service;

import me.alex.dpl.annotation.AutoLoadable;
import org.atteo.classindex.processor.ClassIndexProcessor;

public class AutoLoadableClassIndexProcessor extends ClassIndexProcessor {

    public AutoLoadableClassIndexProcessor() {
        indexAnnotations(AutoLoadable.class);
    }
}
