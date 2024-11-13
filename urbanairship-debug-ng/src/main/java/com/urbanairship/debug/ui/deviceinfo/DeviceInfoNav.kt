package com.urbanairship.debug.ui.deviceinfo

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug.ui.channel.ChannelInfoScreens
import com.urbanairship.debug.ui.contact.ContactScreens

internal fun NavGraphBuilder.deviceInfoNav(navController: NavController) {
    navigation(
        route = TopLevelScreens.DeviceInfo.route,
        startDestination = DeviceInfoScreens.rootScreen.route,
    ) {

        composable(DeviceInfoScreens.rootScreen.route) {
            DeviceInfoScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(DeviceInfoScreens.EditAttributes.route) {
            NavigationSelectionScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
                title = "Manage Attributes",
                items = listOf(
                    NavigationSelectionItem(
                        name = "Channel",
                        route = ChannelInfoScreens.Attributes.route
                    ),
                    NavigationSelectionItem(
                        name = "Contact",
                        route = ContactScreens.Attributes.route
                    )
                )
            )
        }

        composable(DeviceInfoScreens.EditTagGroups.route) {
            NavigationSelectionScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
                title = "Manage Tag Groups",
                items = listOf(
                    NavigationSelectionItem(
                        name = "Channel",
                        route = ChannelInfoScreens.TagGroups.route
                    ),
                    NavigationSelectionItem(
                        name = "Contact",
                        route = ContactScreens.TagGroups.route
                    )
                )
            )
        }
    }
}
