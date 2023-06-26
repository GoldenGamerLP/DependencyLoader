package me.alex.dpl.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor as dependency constructor.
 * This means that the constructor will be used to create an instance of the class.
 * Any parameters of the constructor will be seen as dependencies.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(java.lang.annotation.ElementType.CONSTRUCTOR)
@Documented
public @interface DependencyConstructor {


}
