/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

/**
 * A view that resolves a color, drawable or media URL once, when it's set up, rather than on every
 * state update, so a selector's `environment_conditions` would otherwise stay at the values it was
 * built with.
 *
 * Its model calls [onEnvironmentConditionsChanged] when the layout's chosen values change, in a
 * layout whose selectors name them: before it re-applies the view's background and border, and
 * before the state update that carries the values.
 */
internal interface EnvironmentConditionsSensitive {

    /** Resolves again whatever this view resolved once, against the context's current values. */
    fun onEnvironmentConditionsChanged()
}
