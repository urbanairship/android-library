/* Copyright Airship and Contributors */
package com.urbanairship.modules

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.XmlRes
import com.urbanairship.AirshipComponent
import com.urbanairship.actions.ActionRegistry

/**
 * Airship Module.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class Module protected constructor(
    public val components: Set<AirshipComponent>,
    @field:XmlRes private val actionsXmlId: Int = 0
) {

    /**
     * Called to register actions.
     *
     * @param context The context.
     * @param registry The registry.
     */
    public fun registerActions(context: Context, registry: ActionRegistry) {
        if (actionsXmlId != 0) {
            registry.registerActions(context, actionsXmlId)
        }
    }

    public companion object {

        /**
         * Factory method to create a module for a single component.
         *
         * @param component The component.
         * @param actionsXmlId The actions XML resource ID, or 0 if not available.
         * @return The module.
         */
        public fun singleComponent(
            component: AirshipComponent,
            @XmlRes actionsXmlId: Int
        ): Module {
            return Module(setOf(component), actionsXmlId)
        }

        /**
         * Factory method to create a module for multiple component.
         *
         * @param components The components.
         * @param actionsXmlId The actions XML resource ID, or 0 if not available.
         * @return The module.
         */
        public fun multipleComponents(
            components: Collection<AirshipComponent>,
            @XmlRes actionsXmlId: Int
        ): Module {
            return Module(HashSet(components), actionsXmlId)
        }
    }
}
