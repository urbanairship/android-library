/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.channel

import androidx.compose.ui.graphics.vector.ImageVector
import com.urbanairship.debug.ui.Screen
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug.R

internal enum class ChannelInfoScreens(
    override val titleRes: Int,
    override val descRes: Int? = null,
    override val icon: ImageVector? = null,
    override val isRoot: Boolean = false,
) : Screen {
    // Root screen
    Root(
        titleRes = R.string.ua_debug_channel_title,
        isRoot = true
    ),
    Tags(titleRes = R.string.ua_debug_tags_list_title),
    TagGroups(titleRes = R.string.ua_debug_tag_groups_list_title),
    Attributes(titleRes = R.string.ua_debug_attributes_list_title),
    SubscriptionLists(titleRes = R.string.ua_debug_subscription_list_title);

    override val isTopLevel: Boolean = false

    val route: String = TopLevelScreens.Channel.route + "/$name"
}
