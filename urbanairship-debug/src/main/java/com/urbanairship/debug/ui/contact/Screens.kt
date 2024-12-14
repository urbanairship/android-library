/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.ui.Screen
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug.R

internal enum class ContactScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
) : Screen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_contacts_title,
        isRoot = true
    ),
    NamedUser(titleRes = R.string.ua_debug_contacts_named_user_title),
    TagGroups(titleRes = 0),
    Attributes(titleRes = 0),
    SubscriptionList(titleRes = R.string.ua_debug_contacts_subscription_lists_title),
    AddChannel(titleRes = R.string.ua_debug_contacts_add_channel_title);

    override val isTopLevel: Boolean = false

    val route: String = TopLevelScreens.Contacts.route + "/$name"
}

internal enum class ContactChannelScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
) : Screen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_contacts_add_channel_title,
        isRoot = true
    ),
    OpenChannel(titleRes = R.string.ua_debug_contacts_named_user_title),
    SMSChannel(titleRes = 0),
    EmailChannel(titleRes = 0),
    EmailChannelAddProperty(titleRes = R.string.ua_debug_contacts_channel_email_property_title);

    override val isTopLevel: Boolean = false

    val route: String = ContactScreens.AddChannel.route + "/$name"
}
