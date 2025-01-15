/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.privacymanager

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.ui.Screen
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.DebugScreen

internal enum class PrivacyScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
) : Screen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_privacy_title,
        isRoot = true
    );

    override val isTopLevel: Boolean = false

    val route: String = DebugScreen.PrivacyManager.route + "/$name"
}
