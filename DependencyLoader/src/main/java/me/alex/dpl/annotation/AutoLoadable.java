package me.alex.dpl.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as auto-loadable.
 * This means that the class will be loaded automatically by the {@link me.alex.dpl.DependencyManager} when the {@link me.alex.dpl.DependencyManager#init(boolean)} method is called.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(java.lang.annotation.ElementType.TYPE)
public @interface AutoLoadable {

}
