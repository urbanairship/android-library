package com.urbanairship.debug.ui.automations

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.urbanairship.debug.ui.TopLevelScreens

internal fun NavGraphBuilder.automationNav(navController: NavController) {
    navigation(
        route = TopLevelScreens.Automations.route,
        startDestination = AutomationScreens.Root.route,
    ) {
        composable(AutomationScreens.Root.route) {
            AutomationScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(
            route = "${AutomationScreens.Details.route}/{name}/{id}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) {
            AutomationDetailsScreen(
                name = requireNotNull(it.arguments?.getString("name")),
                id = requireNotNull(it.arguments?.getString("id")),
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}
