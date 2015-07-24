package com.urbanairship;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)

/**
 * This annotation is added to all public fields
 * to avoid the proguard whitelist
 *
 * Adding the following line to the proguard.cfg file
 * will preserve all annotations
 * "--keepattributes *Annotation*"
 *
 * @author Urban Airship
 */
public @interface PropertyName {
    String name();
}
