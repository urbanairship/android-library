package com.urbanairship.debug.ui.appinfo

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug2.R
import com.urbanairship.debug.ui.Screen as BaseScreen

internal enum class AppInfoScreen(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
) : BaseScreen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_app_info_title,
        isRoot = true
    );

    override val isTopLevel: Boolean = false

    val route: String = TopLevelScreens.DeviceInfo.route + "/$name"
}
