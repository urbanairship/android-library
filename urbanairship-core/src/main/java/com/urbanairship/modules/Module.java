/* Copyright Airship and Contributors */

package com.urbanairship.modules;

import com.urbanairship.AirshipComponent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Airship Module.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Module {

    @NonNull
    private final Set<? extends AirshipComponent> components;

    protected Module(@NonNull Set<? extends AirshipComponent> components) {
        this.components = components;
    }

    /**
     * Factory method to create a module for a single component.
     *
     * @param component The component.
     * @return The module.
     */
    public static Module singleComponent(@NonNull AirshipComponent component) {
        return new Module(Collections.singleton(component));
    }

    /**
     * Factory method to create a module for multiple component.
     *
     * @param components The components.
     * @return The module.
     */
    public static Module multipleComponents(@NonNull Collection<AirshipComponent> components) {
        return new Module(new HashSet<>(components));
    }

    /**
     * Gets the Airship components for the module.
     *
     * @return The modules's Airship components.
     */
    @NonNull
    public Set<? extends AirshipComponent> getComponents() {
        return components;
    }
}
