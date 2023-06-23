package me.alex.dpl.annotation;

import java.lang.annotation.Retention;

/**
 * Marks a field as injectable.
 * This means that the field will be injected with an instance of the class.
 * The fields type will be seen as dependency.
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Inject {
}
