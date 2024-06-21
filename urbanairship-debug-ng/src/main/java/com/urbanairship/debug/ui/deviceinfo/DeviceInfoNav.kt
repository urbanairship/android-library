package com.urbanairship.debug.ui.deviceinfo

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.urbanairship.debug.ui.TopLevelScreens

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

        composable(DeviceInfoScreens.EditTags.route) {
            EditTagsScreen(
                onNavigateUp = { navController.popBackStack() },
            )
        }
    }
}
