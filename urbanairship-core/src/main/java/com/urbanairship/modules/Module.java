/* Copyright Airship and Contributors */

package com.urbanairship.modules;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.actions.ActionRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.XmlRes;

/**
 * Airship Module.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Module {

    @NonNull
    private final Set<? extends AirshipComponent> components;

    @XmlRes
    private final int actionsXmlId;

    protected Module(@NonNull Set<? extends AirshipComponent> components) {
        this(components, 0);
    }

    protected Module(@NonNull Set<? extends AirshipComponent> components, @XmlRes int actionsXmlId) {
        this.components = components;
        this.actionsXmlId = actionsXmlId;
    }

    /**
     * Factory method to create a module for a single component.
     *
     * @param component The component.
     * @param actionsXmlId The actions XML resource ID, or 0 if not available.
     * @return The module.
     */
    public static Module singleComponent(@NonNull AirshipComponent component, @XmlRes int actionsXmlId) {
        return new Module(Collections.singleton(component), actionsXmlId);
    }

    /**
     * Factory method to create a module for multiple component.
     *
     * @param components The components.
     * @param actionsXmlId The actions XML resource ID, or 0 if not available.
     * @return The module.
     */
    public static Module multipleComponents(@NonNull Collection<AirshipComponent> components, @XmlRes int actionsXmlId) {
        return new Module(new HashSet<AirshipComponent>(components), actionsXmlId);
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

    /**
     * Called to register actions.
     *
     * @param context The context.
     * @param registry The registry.
     */
    public void registerActions(@NonNull Context context, @NonNull ActionRegistry registry) {
        if (actionsXmlId != 0) {
            registry.registerActions(context, actionsXmlId);
        }
    }

}
