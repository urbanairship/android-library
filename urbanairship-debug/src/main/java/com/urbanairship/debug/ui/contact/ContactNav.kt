/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.urbanairship.UAirship
import com.urbanairship.channel.SubscriptionListEditor
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.ScopedSubscriptionListEditor
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug.ui.channel.AttributeEditScreen
import com.urbanairship.debug.ui.channel.ChannelInfoScreens
import com.urbanairship.debug.ui.channel.SubscriptionListProvider
import com.urbanairship.debug.ui.channel.SubscriptionListsScreen
import com.urbanairship.debug.ui.channel.TagGroupsScreen

internal fun NavGraphBuilder.contactsNav(navController: NavController) {
    navigation(
        route = TopLevelScreens.Contacts.route,
        startDestination = ContactScreens.Root.route
    ) {
        composable(route = ContactScreens.Root.route) {
            ContactsScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(ContactScreens.NamedUser.route) {
            NamedUserScreen(onNavigateUp = { navController.popBackStack() })
        }

        composable(ContactScreens.TagGroups.route) {
            TagGroupsScreen(
                editorProvider = {
                    if (UAirship.isFlying()) {
                        UAirship.shared().contact.editTagGroups()
                    } else {
                        null
                    }
                },
                onNavigateUp = { navController.popBackStack() }
            )
        }

        composable(ContactScreens.Attributes.route) {
            AttributeEditScreen(
                editorProvider = {
                    if (UAirship.isFlying()) {
                        UAirship.shared().contact.editAttributes()
                    } else {
                        null
                    }
                },
                onNavigateUp = { navController.popBackStack() }
            )
        }

        composable(ContactScreens.SubscriptionList.route) {
            ScopedSubscriptionListsScreen(
                provider = object : ScopedSubscriptionListProvider {
                    override fun getEditor(): ScopedSubscriptionListEditor? {
                        return if (UAirship.isFlying()) {
                            UAirship.shared().contact.editSubscriptionLists()
                        } else {
                            null
                        }
                    }

                    override suspend fun fetch(): Result<Map<String, Set<Scope>>> {
                        return UAirship.shared().contact.fetchSubscriptionLists()
                    }
                },
                onNavigateUp = { navController.popBackStack() }
            )
        }

        composable(ContactScreens.AddChannel.route) {
            AddChannelScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(ContactChannelScreens.SMSChannel.route) {
            CreateSmsChannelScreen(
                onNavigateUp = { navController.popBackStack() }
            )
        }

        composable(ContactChannelScreens.OpenChannel.route) {
            CreateOpenChannelScreen(
                onNavigateUp = { navController.popBackStack() }
            )
        }

        composable(route = ContactChannelScreens.EmailChannel.route) {
            CreateEmailChannelScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
                createdProperty = {
                    navController
                        .currentBackStackEntry
                        ?.savedStateHandle
                        ?.remove(KEY_CREATED_PROPERTY)
                }
            )
        }

        composable(route = ContactChannelScreens.EmailChannelAddProperty.route) {
            AddEventPropertyScreen(onNavigateUp = {
                navController
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_CREATED_PROPERTY, it)

                navController.popBackStack()
            })
        }
    }
}

private const val KEY_CREATED_PROPERTY = "created"
