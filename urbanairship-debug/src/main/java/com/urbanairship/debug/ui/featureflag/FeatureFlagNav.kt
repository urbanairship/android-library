/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.featureflag

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.urbanairship.debug.ui.DebugScreen

internal fun NavGraphBuilder.featureFlagNav(navController: NavController) {
    navigation(
        route = DebugScreen.FeatureFlags.route,
        startDestination = FeatureFlagScreens.Root.route
    ) {
        composable(route = FeatureFlagScreens.Root.route) {
            FeatureFlagScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(
            route = "${FeatureFlagScreens.Details.route}/{name}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
            )
        ) {
            FeatureFlagDetailsScreen(
                name = requireNotNull(it.arguments?.getString("name")),
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}
