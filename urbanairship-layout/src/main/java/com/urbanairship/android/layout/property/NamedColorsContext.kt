/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import android.content.ContextWrapper

/**
 * A context that carries the named colors in scope, so a color reference resolved from a view's
 * context finds the color it names.
 *
 * A layout's root wraps the context its views are created with, and async view content wraps it
 * again with its own colors laid over the scene's; the innermost wrapper wins.
 */
internal class NamedColorsContext(
    base: Context,
    private val colors: NamedColors
) : ContextWrapper(base) {

    companion object {

        /** Returns [context] wrapped to carry [colors], or [context] itself where they're already the innermost. */
        fun wrap(context: Context, colors: NamedColors): Context =
            if (find(context)?.colors === colors) context else NamedColorsContext(context, colors)

        /** Returns the color [name] names in [context]'s layout, or opaque black where it names none. */
        fun colorNamed(context: Context, name: String): Color =
            (find(context)?.colors ?: NamedColors.EMPTY).colorNamed(name)

        private fun find(context: Context): NamedColorsContext? {
            var current: Context? = context
            while (current != null) {
                if (current is NamedColorsContext) return current
                current = (current as? ContextWrapper)?.baseContext
            }
            return null
        }
    }
}
