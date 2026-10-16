/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

/**
 * A view that resolves a color, drawable or media URL once, when it's set up, rather than on every
 * state update, so a selector's `environment_conditions` would otherwise stay at the values it was
 * built with.
 *
 * Its model calls [onEnvironmentConditionsChanged] when the layout's chosen values change, in a
 * layout whose selectors name them, while it handles the state update that carries them: before
 * it re-applies the view's background and border, and before its listener's `onStateUpdated`. A
 * view can rely on that order, as `LabelView` does by clearing the cache `onStateUpdated` reads.
 */
internal interface EnvironmentConditionsSensitive {

    /** Resolves again whatever this view resolved once, against the context's current values. */
    fun onEnvironmentConditionsChanged()
}
