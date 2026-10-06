/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.urbanairship.ai.Usage
import com.urbanairship.android.layout.ai.sceneTextInput
import com.urbanairship.debug.ui.DebugScreen
import com.urbanairship.debug.ui.components.DebugScreen as DebugScreenScaffold
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.iam.ai.inAppMessageSuppression

internal fun NavGraphBuilder.aiNav(navController: NavController) {
    navigation(
        route = DebugScreen.AI.route,
        startDestination = AIScreens.Root.route
    ) {
        composable(route = AIScreens.Root.route) {
            AiRootScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(
            route = "${AIScreens.Usage.route}/{usageKey}",
            arguments = listOf(navArgument("usageKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val usageKey = requireNotNull(backStackEntry.arguments?.getString("usageKey"))
            when (usageKey) {
                Usage.inAppMessageSuppression.rawValue ->
                    IaaSuppressionScreen(onNavigateUp = { navController.popBackStack() })
                Usage.sceneTextInput.rawValue ->
                    SceneTextInputScreen(onNavigateUp = { navController.popBackStack() })
                EMBEDDED_SELECTION_USAGE_KEY ->
                    EmbeddedSelectionPlaceholderScreen(onNavigateUp = { navController.popBackStack() })
                else -> UnknownUsageScreen(usageKey = usageKey, onNavigateUp = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun UnknownUsageScreen(usageKey: String, onNavigateUp: () -> Unit) {
    DebugScreenScaffold(
        title = "Usage: $usageKey",
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        Text(
            text = "No debug UI for usage \"$usageKey\"",
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Reserved spot for the embedded selection sandbox — AI ranking of embedded content candidates
 * against a free-text prompt, mirroring iOS's `AirshipDebugEmbeddedSelectionView`. Left empty
 * because Android has no `Usage<EmbeddedSelectionSubject>`, evaluation, or ranking model yet;
 * this screen just holds the row and route so the sandbox can be built the moment that feature
 * ships, without redoing navigation.
 */
@Composable
private fun EmbeddedSelectionPlaceholderScreen(onNavigateUp: () -> Unit) {
    DebugScreenScaffold(
        title = "Embedded Selection",
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Not available yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "AI ranking of embedded content candidates by a free-text prompt hasn't " +
                    "shipped on Android yet. This sandbox will be filled in once the usage, " +
                    "subject, and evaluation land in the SDK.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
