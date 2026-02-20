/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter.compose.ui

import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.compose.ui.theme.MessageCenterTheme

/** Manages the global [MessageCenterTheme]. */
internal object MessageCenterThemeManager {
    private var theme: MessageCenterTheme? = null

    /** Get the current theme, or a default theme if none has been set. */
    fun getTheme(): MessageCenterTheme {
        return theme ?: MessageCenterTheme()
    }

    /** Set the current theme. */
    fun setTheme(theme: MessageCenterTheme) {
        this.theme = theme
    }
}

/**
 * The global [MessageCenterTheme] used to style the Message Center when shown in the default
 * `MessageCenterActivity`. If not set, a default theme will be used.
 *
 * When embedding Message Center composables in an existing Compose hierarchy, use the
 * [MessageCenterTheme] composable to set the theme for the embedded hierarchy instead of setting
 * this property.
 */
@Suppress("UnusedReceiverParameter")
public var MessageCenter.theme: MessageCenterTheme
    get() = MessageCenterThemeManager.getTheme()
    set(value) = MessageCenterThemeManager.setTheme(value)
