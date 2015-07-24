package com.urbanairship;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)

/**
 * This annotation can be added to public fields specifying the class
 * to use when resolving a string property value
 */
public @interface ConstantClass {
    String name();
}
