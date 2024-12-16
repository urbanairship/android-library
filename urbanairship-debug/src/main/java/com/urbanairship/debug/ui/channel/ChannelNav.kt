/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.channel

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.urbanairship.UAirship
import com.urbanairship.channel.SubscriptionListEditor
import com.urbanairship.debug.ui.DebugScreen

internal fun NavGraphBuilder.channelNav(navController: NavController) {
    navigation(
        route = DebugScreen.Channel.route,
        startDestination = ChannelInfoScreens.Root.route
    ) {
        composable(route = ChannelInfoScreens.Root.route) {
            ChannelScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(ChannelInfoScreens.Tags.route) {
            TagsListScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(ChannelInfoScreens.TagGroups.route) {
            TagGroupsScreen(
                editorProvider = {
                    if (UAirship.isFlying()) {
                        UAirship.shared().channel.editTagGroups()
                    } else {
                        null
                    }
                },
                onNavigateUp = { navController.popBackStack() }
            )
        }

        composable(ChannelInfoScreens.Attributes.route) {
            AttributeEditScreen(
                editorProvider = {
                    if (UAirship.isFlying()) {
                        UAirship.shared().channel.editAttributes()
                    } else {
                        null
                    }
                },
                onNavigateUp = { navController.popBackStack() }
            )
        }

        composable(ChannelInfoScreens.SubscriptionLists.route) {
            SubscriptionListsScreen(
                provider = object : SubscriptionListProvider {
                    override fun getEditor(): SubscriptionListEditor? {
                        return if (UAirship.isFlying()) {
                            UAirship.shared().channel.editSubscriptionLists()
                        } else {
                            null
                        }
                    }

                    override suspend fun fetch(): Result<Set<String>> {
                        return UAirship.shared().channel.fetchSubscriptionLists()
                    }
                },
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}
