package com.urbanairship.debug.ui.appinfo

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.urbanairship.debug.ui.DebugScreen

internal fun NavGraphBuilder.appInfoNav(navController: NavController) {
    navigation(
        route = DebugScreen.AppInfo.route,
        startDestination = AppInfoScreen.Root.route,
    ) {

        composable(AppInfoScreen.Root.route) {
            AppInfoScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
    }
}
