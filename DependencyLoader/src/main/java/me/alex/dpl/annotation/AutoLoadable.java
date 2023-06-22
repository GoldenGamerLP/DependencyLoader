package me.alex.dpl.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
//Only classes with this annotation will be loaded
@Target(java.lang.annotation.ElementType.TYPE)
public @interface AutoLoadable {

}
