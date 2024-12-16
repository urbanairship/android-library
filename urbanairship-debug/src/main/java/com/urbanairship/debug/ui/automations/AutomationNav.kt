package com.urbanairship.debug.ui.automations

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.urbanairship.debug.ui.DebugScreen

internal fun NavGraphBuilder.automationNav(navController: NavController) {
    navigation(
        route = DebugScreen.Automations.route,
        startDestination = AutomationScreens.Root.route,
    ) {
        composable(AutomationScreens.Root.route) {
            AutomationRootScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(AutomationScreens.Automations.route) {
            AutomationScreen(
                displaySource = DisplaySource.AUTOMATIONS,
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(AutomationScreens.Experiments.route) {
            AutomationScreen(
                displaySource = DisplaySource.EXPERIMENTS,
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(
            route = "${AutomationScreens.Details.route}/{source}/{name}/{id}",
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) {
            AutomationDetailsScreen(
                source = DisplaySource.fromRoute(requireNotNull(it.arguments?.getString("source"))),
                name = requireNotNull(it.arguments?.getString("name")),
                id = requireNotNull(it.arguments?.getString("id")),
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}
