package com.urbanairship.debug.ui.deviceinfo

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug2.R
import com.urbanairship.debug.ui.Screen as BaseScreen

internal enum class DeviceInfoSection {
    SDK_FEATURES,
    USER_SETTINGS,
    ANALYTICS_SETTINGS,
    IN_APP_AUTOMATION_SETTINGS,
    DEVICE_INFO
}

internal enum class DeviceInfoScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
    val section: DeviceInfoSection? = null,
) : BaseScreen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_device_info_title,
        isRoot = true,
    ),

    // Sub-nav screens
    EditNamedUser(
        titleRes = R.string.ua_debug_named_user_title,
        section = DeviceInfoSection.USER_SETTINGS,
    ),
    EditAttributes(
        titleRes = R.string.ua_debug_attributes_title,
        section = DeviceInfoSection.USER_SETTINGS,
    ),
    EditTags(
        titleRes = R.string.ua_debug_tags_title,
        section = DeviceInfoSection.USER_SETTINGS,
    ),
    EditTagGroups(
        titleRes = R.string.ua_debug_tag_groups_title,
        section = DeviceInfoSection.USER_SETTINGS,
    ),
    EditAssociatedIdentifiers(
        titleRes = R.string.ua_debug_associated_identifiers_title,
        section = DeviceInfoSection.ANALYTICS_SETTINGS
    );

    override val isTopLevel: Boolean = false

    val route: String = TopLevelScreens.DeviceInfo.route + "/$name"

    internal companion object {
        val rootScreen: DeviceInfoScreens = DeviceInfoScreens.entries.first { it.isRoot }
        val topLevelScreens: List<DeviceInfoScreens> = DeviceInfoScreens.entries.filter { it.isTopLevel }

        private val screensByRoute by lazy { DeviceInfoScreens.entries.associateBy { it.route } }
        fun forRoute(route: String?): DeviceInfoScreens? = route?.let { screensByRoute[route] }
    }
}