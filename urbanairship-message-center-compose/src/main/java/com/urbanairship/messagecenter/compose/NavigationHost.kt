package com.urbanairship.messagecenter.compose

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.compose.ui.MessageCenterView
import com.urbanairship.messagecenter.compose.ui.MessageCenterViewModelFactory
import com.urbanairship.messagecenter.compose.ui.MessageView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
public fun MessageCenterNavigationHost(
    navController: NavHostController = rememberNavController(),
    messageIdToDisplay: StateFlow<String?> = MutableStateFlow(null),
    modifier: Modifier = Modifier.fillMaxSize(),
    onNavigateUp: () -> Unit = {}
) {
    var presentedRoutes by remember { mutableStateOf(setOf<String>()) }

    NavHost(
        navController = navController,
        startDestination = MessageCenterScreen.Root.route,
        modifier = modifier,
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(220),
                targetOffsetX = { it }
            )
        }
    ) {
        composable(
            route = MessageCenterScreen.Root.route,
        ) {
            MessageCenterView(
                messageIdToDisplay = messageIdToDisplay,
                navigateToMessageId = { messageId ->
                    navController.navigate(MessageCenterScreen.ShowMessage.createRoute(messageId))
                }
            )
        }

        composable(
            route = MessageCenterScreen.MessageList.route
        ) {
            MessageCenterView(
                messageIdToDisplay = messageIdToDisplay,
                navigateToMessageId = { messageId ->
                    navController.navigate(MessageCenterScreen.ShowMessage.createRoute(messageId))
                }
            )
        }

        composable(
            route = MessageCenterScreen.ShowMessage.route,
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(220),
                    initialOffsetX = { it }
                )
            },
        ) { entry ->
            val messageId = entry.arguments?.getString("messageId")
            MessageView(
                messageId = messageId,
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}
