package me.alex.dpl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as auto-runnable.
 * This means that the method will be called after {@link Inject} and {@link DependencyConstructor} annotated methods have been called.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoRun {

    int priority() default 0;

    boolean async() default false;
}
