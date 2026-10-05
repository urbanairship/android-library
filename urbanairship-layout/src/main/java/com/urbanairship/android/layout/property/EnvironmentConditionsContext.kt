/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import android.content.ContextWrapper
import com.urbanairship.json.JsonMap

/**
 * A context that knows its layout's environment conditions, so a color or media URL selector
 * resolved from a view's context can match `environment_conditions`.
 *
 * A layout's root wraps the context its views are created with; every view below shares it.
 *
 * @param conditions the layout's environment conditions' chosen values as they stand now.
 */
internal class EnvironmentConditionsContext(
    base: Context,
    private val conditions: () -> JsonMap
) : ContextWrapper(base) {

    companion object {

        /**
         * Returns [context] wrapped to carry [conditions], or [context] itself where it already
         * carries a layout's.
         */
        fun wrap(context: Context, conditions: () -> JsonMap): Context =
            if (find(context) != null) context else EnvironmentConditionsContext(context, conditions)

        /**
         * Returns the context [context] wraps when it's one of these, else [context]: what a host's
         * custom view handler expects, an activity among them.
         */
        fun unwrap(context: Context): Context =
            (context as? EnvironmentConditionsContext)?.baseContext ?: context

        /** Returns the chosen values of the layout [context] belongs to, or none outside one. */
        fun conditionsOf(context: Context): JsonMap = find(context)?.conditions?.invoke() ?: JsonMap.EMPTY_MAP

        private fun find(context: Context): EnvironmentConditionsContext? {
            var current: Context? = context
            while (current != null) {
                if (current is EnvironmentConditionsContext) return current
                current = (current as? ContextWrapper)?.baseContext
            }
            return null
        }
    }
}
