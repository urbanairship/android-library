/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.featureflag

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.ui.Screen
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.DebugScreen

internal enum class FeatureFlagScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
) : Screen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_feature_flags_title,
        isRoot = true
    ),
    Details(titleRes = 0);

    override val isTopLevel: Boolean = false

    val route: String = DebugScreen.FeatureFlags.route + "/$name"
}