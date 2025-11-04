package com.urbanairship.debug.ui.preferencecenter

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.DebugScreen
import com.urbanairship.debug.ui.Screen as BaseScreen

internal enum class PreferenceCenterScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: Int? = null,
    override val isRoot: Boolean = false,
) : BaseScreen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_pref_centers_title,
        isRoot = true
    );

    override val isTopLevel: Boolean = false

    val route: String = DebugScreen.PrefCenters.route + "/$name"
}
