/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.view.View

/**
 * Interface for Views that measure their content unbounded on an axis and can say what size
 * percent-sized descendants should resolve against on it — in practice the scroll layouts, which
 * measure content taller (or wider) than themselves and hand down their viewport.
 *
 * A [android.view.View.MeasureSpec] can't carry this: it holds a single size and mode, so it has
 * no way to say "resolve percentages against 600px, but measure as tall as you like". Descendants
 * that need the number walk up and read it from here instead of it being pushed down, which keeps
 * the wrapper views in between — button and toggle layouts, async layouts — out of it entirely.
 * They're transparent in the hierarchy, and this way they stay that way.
 *
 * A non-positive value means "no base on this axis", leaving percent-sized descendants to fall
 * back to sizing themselves to their content rather than collapsing.
 */
internal interface PercentBaseProvider {

    /** Size percent-sized descendants resolve widths against, in pixels. */
    val percentBaseWidth: Int

    /** Size percent-sized descendants resolve heights against, in pixels. */
    val percentBaseHeight: Int
}

/**
 * Borrows a percent base from the nearest ancestor that measures its content unbounded.
 *
 * Only meaningful for an axis this view was measured UNSPECIFIED on. That means every ancestor up to
 * the provider is unbounded on the same axis — a bounded one would have handed down AT_MOST or
 * EXACTLY, and the view would have a size of its own to resolve against instead. So the nearest
 * provider is always the right one, and the wrapper views in between (button and toggle layouts,
 * async layouts) don't have to forward anything.
 *
 * Returns 0 when there's no provider above, which leaves percent-sized children to fall back to
 * their content rather than collapsing.
 */
internal fun View.borrowedPercentBase(horizontal: Boolean): Int {
    var node = parent
    while (node is View) {
        if (node is PercentBaseProvider) {
            val base = if (horizontal) node.percentBaseWidth else node.percentBaseHeight
            if (base > 0) return base
        }
        node = node.parent
    }
    return 0
}
