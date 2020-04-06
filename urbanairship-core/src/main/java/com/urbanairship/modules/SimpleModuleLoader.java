/* Copyright Airship and Contributors */
package com.urbanairship.modules;

import com.urbanairship.AirshipComponent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Module loader implementation.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SimpleModuleLoader implements ModuleLoader {

    private final Set<? extends AirshipComponent> components;

    private SimpleModuleLoader(@NonNull Set<? extends AirshipComponent> components) {
        this.components = components;
    }

    public static SimpleModuleLoader singleComponent(@NonNull AirshipComponent component) {
        return new SimpleModuleLoader(Collections.singleton(component));
    }

    public static SimpleModuleLoader multipleComponents(@NonNull Set<? extends AirshipComponent> components) {
        return new SimpleModuleLoader(new HashSet<>(components));
    }

    @Override
    public Set<? extends AirshipComponent> getComponents() {
        return components;
    }

}
