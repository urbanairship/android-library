package com.urbanairship.debug.ui.push

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.urbanairship.debug.ui.TopLevelScreens

internal fun NavGraphBuilder.pushNav(navController: NavController) {
    navigation(
        route = TopLevelScreens.Pushes.route,
        startDestination = PushScreens.Root.route,
    ) {
        composable(PushScreens.Root.route) {
            PushScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(
            route = "${PushScreens.Details.route}/{id}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType }
            )
        ) {
            PushDetailsScreen(
                id = requireNotNull(it.arguments?.getString("id")),
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}