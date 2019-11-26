/* Copyright Airship and Contributors */

package com.urbanairship.modules;

import com.urbanairship.AirshipComponent;

import java.util.Set;

import androidx.annotation.RestrictTo;

/**
 * Module loader.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ModuleLoader {

    /**
     * Gets the Airship components for the module.
     *
     * @return The modules's Airship components.
     */
    Set<? extends AirshipComponent> getComponents();

}
