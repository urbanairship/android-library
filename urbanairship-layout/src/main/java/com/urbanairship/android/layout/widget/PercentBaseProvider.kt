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
 * A viewport reaches only as far as the lengths that are measured against it, so the walk stops at
 * an ancestor that sizes itself to its content: what is inside such a view is as long as its own
 * content, however many scroll layouts sit above it. Without that, a `100%` row a few levels down
 * takes a whole viewport for itself — a quiz with two options rendered one per screen, its second
 * option and its Next button a screen and two screens below the fold.
 *
 * Returns 0 when there's no base to be had, which leaves percent-sized children to fall back to
 * their content rather than collapsing.
 */
internal fun View.borrowedPercentBase(horizontal: Boolean): Int {
    var node = parent
    while (node is View) {
        if (node is AutoSizeProvider && node.isAutoSized(horizontal)) return 0
        if (node is PercentBaseProvider) {
            val base = if (horizontal) node.percentBaseWidth else node.percentBaseHeight
            if (base > 0) return base
        }
        node = node.parent
    }
    return 0
}

/**
 * Interface for Views that know whether their own length on an axis is an allowance they were
 * given or a measurement they arrived at.
 *
 * A [android.view.View.MeasureSpec] can't carry this either. `AT_MOST` says "no more than this",
 * and that reads the same whether it's a real ceiling — a fixed container, the window — or slack
 * an auto-sized ancestor is only passing through on its way to sizing itself to its content.
 *
 * Most of the layout doesn't need the distinction. A stack solving for its own length does: when
 * its percentages sum to 1 or more there is no self-consistent answer, so it takes the whole bound
 * instead. Against a real ceiling that's the fair-share behaviour we want. Against inherited slack
 * it's a land-grab — the bound is the ancestor's content budget, which this stack is supposed to be
 * contributing to rather than consuming, and claiming it starves every sibling.
 */
internal interface AutoSizeProvider {

    /** True when this view sizes itself to its content on the axis, rather than to an allowance. */
    fun isAutoSized(horizontal: Boolean): Boolean
}

/**
 * Whether the length this view was given on an axis is slack inherited from an auto-sized ancestor.
 *
 * Walks up to the nearest view that can answer, the same way [borrowedPercentBase] does and for the
 * same reason: the wrapper views in between — button and toggle layouts, async layouts — hold no
 * size of their own and pass their spec straight through, so they'd only have to forward the answer
 * unchanged. The first ancestor that owns a length is the one that decides.
 *
 * Returns false when nothing above answers, which keeps the existing behaviour for any hierarchy
 * this doesn't model.
 */
internal fun View.hasAutoSizedAncestor(horizontal: Boolean): Boolean {
    var node = parent
    while (node is View) {
        if (node is AutoSizeProvider) return node.isAutoSized(horizontal)
        node = node.parent
    }
    return false
}

/**
 * Interface for Views that can say whether anything in their subtree gives an axis a length that
 * isn't a share of something above it.
 *
 * A percentage is a share of its parent, so it can only be resolved once the parent has a length to
 * take a share of — it never supplies one. A parent sized to its content takes its length from its
 * children, so children that all decline to supply one leave it nothing to work from: in
 * `H(1 - P) = S`, the extent of everything that isn't a percentage is `S = 0`.
 *
 * Worth answering before measuring rather than solving for. `S / (1 - P)` is 0 for any `P` below 1
 * and has no solution at all at or above it, so the solve either collapses the items or reaches for
 * a bound that isn't ours. Knowing up front that there's no basis lets the percentages fall back to
 * their content, which is stable and leaves them visible.
 */
internal interface LengthBasisProvider {

    /** Whether anything in this subtree gives the axis a length of its own. */
    fun establishesLength(horizontal: Boolean): Boolean
}

/**
 * Whether this view supplies a length on an axis, for a parent deciding if it has a basis to
 * resolve percentages against.
 *
 * Answers true for anything that can't say otherwise. Treating a view that did have content of its
 * own as basis-less hides it outright, where declining to means the size is solved as it was
 * before — so uncertainty belongs on the side of drawing something.
 */
internal fun View.establishesLength(horizontal: Boolean): Boolean =
    (this as? LengthBasisProvider)?.establishesLength(horizontal) ?: true
