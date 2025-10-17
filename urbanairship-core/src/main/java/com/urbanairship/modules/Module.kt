/* Copyright Airship and Contributors */
package com.urbanairship.modules

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.actions.ActionsManifest

/**
 * Airship Module.
 *
 * This class represents a logical grouping of one or more [AirshipComponent] instances
 * and an optional [ActionsManifest]. Modules are created by internal Airship
 * libraries to bundle related functionality.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class Module protected constructor(
    /**
     * The set of components belonging to this module.
     */
    public val components: Set<AirshipComponent>,

    /**
     * An optional manifest describing actions provided by this module.
     */
    public val actionsManifest: ActionsManifest?
) {

    public companion object {

        /**
         * Creates a [Module] containing a single [AirshipComponent].
         *
         * @param component The single component for the module.
         * @param actionsManifest An optional manifest of actions provided by the component.
         * @return A new [Module] instance.
         */
        public fun singleComponent(
            component: AirshipComponent, actionsManifest: ActionsManifest? = null
        ): Module {
            return Module(setOf(component), actionsManifest)
        }

        /**
         * Creates a [Module] containing multiple [AirshipComponent] instances.
         *
         * @param components A collection of components for the module.
         * @param actionsManifest An optional manifest of actions provided by these components.
         * @return A new [Module] instance.
         */
        public fun multipleComponents(
            components: Collection<AirshipComponent>, actionsManifest: ActionsManifest? = null
        ): Module {
            return Module(components.toSet(), actionsManifest)
        }
    }
}
