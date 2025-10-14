package com.urbanairship.messagecenter.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.urbanairship.messagecenter.Message
import kotlinx.coroutines.launch
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.Action as MessageAction

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
public fun MessageCenterScreen(
    modifier: Modifier = Modifier,
    state: MessageCenterState = rememberMessageCenterState(),
    showListNavigateUpIcon: Boolean = false,
    onNavigateUp: () -> Unit = {}
) {
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<String?>()
    val scope = rememberCoroutineScope()

    val paneState = scaffoldNavigator.scaffoldState.currentState

    val isTwoPane = paneState.primary == PaneAdaptedValue.Expanded &&
                        paneState.secondary == PaneAdaptedValue.Expanded

    NavigableListDetailPaneScaffold(
        modifier = modifier,
        navigator = scaffoldNavigator,
        listPane = {
            AnimatedPane {
                ListPane(
                    state = state.listState,
                    showNavigateUp = showListNavigateUpIcon,
                    onMessageClick = { message ->
                        scope.launch {
                            scaffoldNavigator.navigateTo(
                                ListDetailPaneScaffoldRole.Detail,
                                contentKey = message.id
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        detailPane = {
            val messageId = scaffoldNavigator.currentDestination?.contentKey
            LaunchedEffect(messageId) {
                messageId?.let { state.messageState.onAction(MessageAction.LoadMessage(it)) }
            }

            AnimatedPane {
                MessagePane(
                    state = state.messageState,
                    showNavigateUp = !isTwoPane,
                    showVerticalDivider = isTwoPane,
                    onNavigateBack = {
                        scope.launch {
                            scaffoldNavigator.navigateBack()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPane(
   state: MessageCenterListState,
   showNavigateUp: Boolean,
   modifier: Modifier = Modifier,
   onNavigateUp: () -> Unit = { },
   onMessageClick: (Message) -> Unit,
) {
    MessageCenterListScreen(
        state = state,
        topBar = {
            MessageCenterDefaults.listTopBar(
                title = "Messages",
                isEditing = state.isEditing,
                navIcon = if (showNavigateUp) Icons.AutoMirrored.Filled.ArrowBack else null,
                onNavigateUp = onNavigateUp,
                actions = { MessageCenterDefaults.listTopBarActions(state) }
            )
        },
        onNavigateUp = onNavigateUp,
        onMessageSelected = onMessageClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagePane(
    state: MessageCenterMessageState,
    showNavigateUp: Boolean,
    showVerticalDivider: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box {
        MessageCenterMessageScreen(
            state = state,
            topBar = { title, scrollBehavior, onNavigateUp ->
                MessageCenterDefaults.messageTopBar(
                    title = title,
                    navIcon = if (showNavigateUp) Icons.AutoMirrored.Filled.ArrowBack else null,
                    onNavigateUp = onNavigateUp,
                    actions = { MessageCenterDefaults.messageTopBarActions(state) },
                    scrollBehavior = scrollBehavior
                )
            },
            onNavigateUp = onNavigateBack,
            onClose = onNavigateBack,
            modifier = modifier
        )

        if (showVerticalDivider) {
            VerticalDivider(
                thickness = 1.dp,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .align(Alignment.CenterStart)
            )
        }
    }
}
