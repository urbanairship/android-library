package com.urbanairship.debug.ui.preferencecenter

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.urbanairship.debug.ui.TopLevelScreens

internal fun NavGraphBuilder.preferenceCenterNav(navController: NavController) {
    navigation(
        route = TopLevelScreens.PrefCenters.route,
        startDestination = PreferenceCenterScreens.Root.route,
    ) {
        composable(PreferenceCenterScreens.Root.route) {
            PreferenceCentersScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
    }
}