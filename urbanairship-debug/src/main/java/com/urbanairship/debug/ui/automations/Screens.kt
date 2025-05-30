package com.urbanairship.debug.ui.automations

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.DebugScreen
import com.urbanairship.debug.ui.Screen as BaseScreen

internal enum class AutomationScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
) : BaseScreen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_automations_title,
        isRoot = true
    ),
    Automations(titleRes = R.string.ua_debug_automations_title),
    Experiments(titleRes = R.string.ua_debug_experiments_title),
    Details(titleRes = 0);


    override val isTopLevel: Boolean = false

    val route: String = DebugScreen.Automations.route + "/$name"
}
