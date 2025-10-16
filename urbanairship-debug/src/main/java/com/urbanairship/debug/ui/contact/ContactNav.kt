/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.urbanairship.Airship
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.ScopedSubscriptionListEditor
import com.urbanairship.debug.ui.DebugScreen
import com.urbanairship.debug.ui.channel.AttributeEditScreen
import com.urbanairship.debug.ui.channel.TagGroupsScreen

internal fun NavGraphBuilder.contactsNav(navController: NavController) {
    navigation(
        route = DebugScreen.Contacts.route,
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
                    if (Airship.isFlying) {
                        Airship.contact.editTagGroups()
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
                    if (Airship.isFlying) {
                        Airship.contact.editAttributes()
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
                        return if (Airship.isFlying) {
                            Airship.contact.editSubscriptionLists()
                        } else {
                            null
                        }
                    }

                    override suspend fun fetch(): Result<Map<String, Set<Scope>>> {
                        return Airship.contact.fetchSubscriptionLists()
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
