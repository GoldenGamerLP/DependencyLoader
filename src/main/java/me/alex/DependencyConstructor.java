package me.alex;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;

@Retention(RetentionPolicy.RUNTIME)
@Target(java.lang.annotation.ElementType.CONSTRUCTOR)
public @interface DependencyConstructor {



}
