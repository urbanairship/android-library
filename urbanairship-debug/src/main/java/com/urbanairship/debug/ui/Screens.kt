package com.urbanairship.debug.ui

import androidx.annotation.RestrictTo
import com.urbanairship.debug.R

internal interface Screen {
    val titleRes: Int
    val descRes: Int?
    val icon: Int?
    val isRoot: Boolean
    val isTopLevel: Boolean
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal enum class DebugScreen(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: Int? = null,
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
        icon = R.drawable.ic_smartphone,
        isTopLevel = true,
    ),
    PrivacyManager(
        titleRes = R.string.ua_debug_privacy_title,
        descRes = R.string.ua_debug_privacy_desc,
        icon = R.drawable.ic_privacy_tip,
        isTopLevel = true,
    ),
    Channel(
        titleRes = R.string.ua_debug_channel_title,
        descRes = R.string.ua_debug_channel_desc,
        icon = R.drawable.ic_login,
        isTopLevel = true,
    ),
    Analytics(
        titleRes = R.string.ua_debug_analytics_title,
        descRes = R.string.ua_debug_events_desc,
        icon = R.drawable.ic_event_note,
        isTopLevel = true,
    ),
    Automations(
        titleRes = R.string.ua_debug_automations_title,
        descRes = R.string.ua_debug_automations_desc,
        icon = R.drawable.ic_smart_toy,
        isTopLevel = true,
    ),
    FeatureFlags(
        titleRes = R.string.ua_debug_feature_flags_title,
        descRes = R.string.ua_debug_feature_flags_desc,
        icon = R.drawable.ic_flag,
        isTopLevel = true,
    ),
    Pushes(
        titleRes = R.string.ua_debug_pushes_title,
        descRes = R.string.ua_debug_pushes_desc,
        icon = R.drawable.ic_message,
        isTopLevel = true,
    ),
    PrefCenters(
        titleRes = R.string.ua_debug_pref_centers_title,
        descRes = R.string.ua_debug_pref_centers_desc,
        icon = R.drawable.ic_checklist,
        isTopLevel = true,
    ),
    Contacts(
        titleRes = R.string.ua_debug_contacts_title,
        descRes = R.string.ua_debug_contacts_desc,
        icon = R.drawable.ic_contact,
        isTopLevel = true,
    ),
    AppInfo(
        titleRes = R.string.ua_debug_app_info_title,
        descRes = R.string.ua_debug_app_info_desc,
        icon = R.drawable.ic_smartphone,
        isTopLevel = true,
    );


    val route: String = this.name

    internal companion object {
        val rootScreen: DebugScreen = entries.first { it.isRoot }
        val topLevelScreens: List<DebugScreen> = entries.filter { it.isTopLevel }

        private val screensByRoute by lazy { entries.associateBy { it.route } }
        fun forRoute(route: String?): DebugScreen? = route?.let { screensByRoute[route] }
    }
}
