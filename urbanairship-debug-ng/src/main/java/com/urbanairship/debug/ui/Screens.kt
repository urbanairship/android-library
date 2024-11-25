package com.urbanairship.debug.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug2.R

internal interface Screen {
    val titleRes: Int
    val descRes: Int?
    val icon: ImageVector?
    val isRoot: Boolean
    val isTopLevel: Boolean
}

internal enum class TopLevelScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
    override val isTopLevel: Boolean = false,
) : Screen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_label,
        isRoot = true
    ),

    // Top level screens
    DeviceInfo(
        titleRes = R.string.ua_debug_device_info_title,
        descRes = R.string.ua_debug_device_info_desc,
        icon = Icons.Rounded.Smartphone,
        isTopLevel = true,
    ),
    PrivacyManager(
        titleRes = R.string.ua_debug_privacy_title,
        descRes = R.string.ua_debug_privacy_desc,
        icon = Icons.Rounded.PrivacyTip,
        isTopLevel = true,
    ),
    Channel(
        titleRes = R.string.ua_debug_channel_title,
        descRes = R.string.ua_debug_channel_desc,
        icon = Icons.AutoMirrored.Rounded.Login,
        isTopLevel = true,
    ),
    Analytics(
        titleRes = R.string.ua_debug_analytics_title,
        descRes = R.string.ua_debug_events_desc,
        icon = Icons.AutoMirrored.Rounded.EventNote,
        isTopLevel = true,
    ),
    Automations(
        titleRes = R.string.ua_debug_automations_title,
        descRes = R.string.ua_debug_automations_desc,
        icon = Icons.Rounded.SmartToy,
        isTopLevel = true,
    ),
    FeatureFlags(
        titleRes = R.string.ua_debug_feature_flags_title,
        descRes = R.string.ua_debug_feature_flags_desc,
        icon = Icons.Rounded.Flag,
        isTopLevel = true,
    ),
    Pushes(
        titleRes = R.string.ua_debug_pushes_title,
        descRes = R.string.ua_debug_pushes_desc,
        icon = Icons.AutoMirrored.Rounded.Message,
        isTopLevel = true,
    ),
    PrefCenters(
        titleRes = R.string.ua_debug_pref_centers_title,
        descRes = R.string.ua_debug_pref_centers_desc,
        icon = Icons.Rounded.Checklist,
        isTopLevel = true,
    ),
    Contacts(
        titleRes = R.string.ua_debug_contacts_title,
        descRes = R.string.ua_debug_contacts_desc,
        icon = Icons.Rounded.ContactPage,
        isTopLevel = true,
    ),
    AppInfo(
        titleRes = R.string.ua_debug_app_info_title,
        descRes = R.string.ua_debug_app_info_desc,
        icon = Icons.Rounded.Smartphone,
        isTopLevel = true,
    );


    val route: String = this.name

    internal companion object {
        val rootScreen: TopLevelScreens = entries.first { it.isRoot }
        val topLevelScreens: List<TopLevelScreens> = entries.filter { it.isTopLevel }

        private val screensByRoute by lazy { entries.associateBy { it.route } }
        fun forRoute(route: String?): TopLevelScreens? = route?.let { screensByRoute[route] }
    }
}
