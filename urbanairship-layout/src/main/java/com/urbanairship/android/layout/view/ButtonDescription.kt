/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible

/**
 * A view that adds to, or orders, the description a `button_layout` generates from what it wraps.
 *
 * The button reads its live views rather than the scene as written, so the description follows
 * the order, text and visibility that state gives them.
 */
internal interface ButtonDescriptionSource {

    /** Returns what this view adds to the description, or null for nothing. */
    fun buttonDescription(): String? = null

    /** Returns the views to read inside this one, in reading order, or null for its children in order. */
    fun childrenInReadingOrder(): List<View>? = null
}

/**
 * Returns the description a button generates for [view]: each shown label's and medium's, in
 * reading order, joined with commas. A button inside it describes itself, so adds nothing.
 *
 * @param view what the button wraps.
 * @return the description, empty when nothing inside adds one.
 */
internal fun generatedButtonDescription(view: View): String =
    descriptionParts(view).joinToString(", ")

private fun descriptionParts(view: View): List<String> {
    if (!view.isVisible || view is ButtonLayoutView) return emptyList()
    val source = view as? ButtonDescriptionSource
    source?.buttonDescription()?.takeIf { it.isNotEmpty() }?.let { return listOf(it) }
    val children = source?.childrenInReadingOrder() ?: (view as? ViewGroup)?.children?.toList() ?: return emptyList()
    return children.flatMap(::descriptionParts)
}
