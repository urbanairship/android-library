package com.urbanairship.debug.ui.home

import androidx.annotation.RestrictTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.urbanairship.debug.ui.DebugScreen
import com.urbanairship.debug.ui.appinfo.appInfoNav
import com.urbanairship.debug.ui.automations.automationNav
import com.urbanairship.debug.ui.channel.channelNav
import com.urbanairship.debug.ui.contact.contactsNav
import com.urbanairship.debug.ui.deviceinfo.deviceInfoNav
import com.urbanairship.debug.ui.events.eventNav
import com.urbanairship.debug.ui.featureflag.featureFlagNav
import com.urbanairship.debug.ui.preferencecenter.preferenceCenterNav
import com.urbanairship.debug.ui.privacymanager.privacyNav
import com.urbanairship.debug.ui.push.pushNav

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
public fun DebugNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier.fillMaxSize(),
    showNavIconOnDebugHomeScreen: Boolean = false,
    onNavigateUpFromHomeScreen: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = DebugScreen.Root.route,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(220)) },
        modifier = modifier
    ) {
        airshipDebugNav(
            navController = navController,
            showNavIconOnDebugHomeScreen = showNavIconOnDebugHomeScreen,
            onNavigateUpFromHomeScreen = onNavigateUpFromHomeScreen
       )
    }
}

internal fun NavGraphBuilder.airshipDebugNav(
    navController: NavHostController,
    showNavIconOnDebugHomeScreen: Boolean = false,
    onNavigateUpFromHomeScreen: () -> Unit = {}
) {
    composable(
        route =  DebugScreen.Root.route
    ) {
        DebugHomeScreen(
            onNavigate = { route -> navController.navigate(route) },
            onNavigateUp = onNavigateUpFromHomeScreen,
            showNavIcon = showNavIconOnDebugHomeScreen
        )
    }

    deviceInfoNav(navController = navController)
    privacyNav(navController = navController)
    appInfoNav(navController = navController)
    preferenceCenterNav(navController = navController)
    automationNav(navController = navController)
    eventNav(navController = navController)
    pushNav(navController = navController)
    featureFlagNav(navController = navController)
    channelNav(navController)
    contactsNav(navController)
}
