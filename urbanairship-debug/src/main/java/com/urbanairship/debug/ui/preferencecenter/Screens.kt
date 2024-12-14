package com.urbanairship.debug.ui.preferencecenter

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.Screen as BaseScreen

internal enum class PreferenceCenterScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
) : BaseScreen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_pref_centers_title,
        isRoot = true
    );

    override val isTopLevel: Boolean = false

    val route: String = TopLevelScreens.PrefCenters.route + "/$name"
}
