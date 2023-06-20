package me.alex.dpl.annotation;

import org.atteo.classindex.IndexAnnotated;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@IndexAnnotated
//Only classes with this annotation will be loaded
@Target(java.lang.annotation.ElementType.TYPE)
public @interface AutoLoadable {

}
