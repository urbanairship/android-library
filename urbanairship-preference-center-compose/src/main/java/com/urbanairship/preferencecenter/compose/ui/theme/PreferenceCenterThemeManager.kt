/* Copyright Airship and Contributors */

package com.urbanairship.preferencecenter.compose.ui.theme

import com.urbanairship.preferencecenter.PreferenceCenter

/** Manages the global [PreferenceCenterTheme]. */
internal object PreferenceCenterThemeManager {
    private var theme: PreferenceCenterTheme? = null

    /** Get the current theme, or a default theme if none has been set. */
    fun getTheme(): PreferenceCenterTheme {
        return theme ?: PreferenceCenterTheme()
    }

    /** Set the current theme. */
    fun setTheme(theme: PreferenceCenterTheme) {
        this.theme = theme
    }
}

/**
 * The global [PreferenceCenterTheme] used to style the Preference Center when shown in the default
 * `PreferenceCenterActivity`. If not set, a default theme will be used.
 *
 * When embedding Preference Center composables in an existing Compose hierarchy, use the
 * [PreferenceCenterTheme] composable to set the theme for the embedded hierarchy instead of setting
 * this property.
 */
public var PreferenceCenter.theme: PreferenceCenterTheme
    get() = PreferenceCenterThemeManager.getTheme()
    set(value) = PreferenceCenterThemeManager.setTheme(value)
