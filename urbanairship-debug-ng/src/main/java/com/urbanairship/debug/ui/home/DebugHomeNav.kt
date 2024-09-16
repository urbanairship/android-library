package com.urbanairship.debug.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug.ui.appinfo.appInfoNav
import com.urbanairship.debug.ui.automations.automationNav
import com.urbanairship.debug.ui.deviceinfo.DeviceInfoScreen
import com.urbanairship.debug.ui.deviceinfo.deviceInfoNav
import com.urbanairship.debug.ui.events.eventNav
import com.urbanairship.debug.ui.featureflag.featureFlagNav
import com.urbanairship.debug.ui.preferencecenter.preferenceCenterNav
import com.urbanairship.debug.ui.push.pushNav

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier.fillMaxSize(),
) {

    NavHost(
        navController = navController,
        startDestination =  TopLevelScreens.Root.route,
        modifier = modifier
    ) {
        composable(
            route =  TopLevelScreens.Root.route
        ) {
            DebugHomeScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        deviceInfoNav(navController = navController)
        appInfoNav(navController = navController)
        preferenceCenterNav(navController = navController)
        automationNav(navController = navController)
        eventNav(navController = navController)
        pushNav(navController = navController)
        featureFlagNav(navController = navController)

        composable(route = TopLevelScreens.Contacts.route) {
            // TODO
            DeviceInfoScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }


    }
}
