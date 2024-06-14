/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.urbanairship.debug.ui.TopLevelScreens

internal fun NavGraphBuilder.eventNav(navController: NavController) {
    navigation(
        route = TopLevelScreens.Events.route,
        startDestination = EventScreens.Root.route,
    ) {
        composable(EventScreens.Root.route) {
            EventScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(
            route = "${EventScreens.Details.route}/{type}/{id}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) {
            EventDetailsScreen(
                type = requireNotNull(it.arguments?.getString("type")),
                id = requireNotNull(it.arguments?.getString("id")),
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}