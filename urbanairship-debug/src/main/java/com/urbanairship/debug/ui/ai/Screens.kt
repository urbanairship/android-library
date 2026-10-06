/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.ai

import com.urbanairship.debug.R
import com.urbanairship.debug.ui.DebugScreen
import com.urbanairship.debug.ui.Screen

internal enum class AIScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: Int? = null,
    override val isRoot: Boolean = false,
) : Screen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_ai_title,
        isRoot = true
    ),
    Usage(titleRes = 0);

    override val isTopLevel: Boolean = false

    val route: String = DebugScreen.AI.route + "/$name"
}
