/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.ui.Screen
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug2.R

internal enum class EventScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
) : Screen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_events_title,
        isRoot = true
    ),
    Details(titleRes = 0);

    override val isTopLevel: Boolean = false

    val route: String = TopLevelScreens.Events.route + "/$name"
}
