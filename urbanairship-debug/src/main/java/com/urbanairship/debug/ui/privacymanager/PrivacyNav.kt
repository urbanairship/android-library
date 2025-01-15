/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.privacymanager

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.urbanairship.debug.ui.DebugScreen

internal fun NavGraphBuilder.privacyNav(navController: NavController) {
    navigation(
        route = DebugScreen.PrivacyManager.route,
        startDestination = PrivacyScreens.Root.route,
    ) {
        composable(PrivacyScreens.Root.route) {
            PrivacyScreen(
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}
