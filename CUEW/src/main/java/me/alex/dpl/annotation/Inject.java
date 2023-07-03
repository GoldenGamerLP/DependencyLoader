package me.alex.dpl.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a field as injectable.
 * This means that the field will be injected with an instance of the class.
 * The fields type will be seen as dependency.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Inject {
}
