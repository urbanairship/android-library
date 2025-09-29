package com.urbanairship.messagecenter.compose.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Predicate
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.ui.view.MessageListViewModel
import com.urbanairship.messagecenter.ui.view.MessageViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
public fun MessageCenterPanesView(
    messageIdToDisplay: StateFlow<String?> = MutableStateFlow(null),
    predicate: Predicate<Message>? = null,
    listViewModel: MessageListViewModel = viewModel(
        modelClass = MessageListViewModel::class.java,
        factory = MessageListViewModel.factory(predicate)
    ),
    messageViewModel: MessageViewModel = viewModel(
        modelClass = MessageViewModel::class.java,
        factory = MessageViewModel.factory()
    ),
    onNavigateUp: () -> Unit = {}
) {
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<String?>()
    val scope = rememberCoroutineScope()

    val paneState = scaffoldNavigator.scaffoldState.currentState
    val isTwoPaneMode = paneState.primary == PaneAdaptedValue.Expanded &&
                        paneState.secondary == PaneAdaptedValue.Expanded

    val panesViewModel: PanesViewModel = viewModel {
        PanesViewModel(messageIdToDisplay)
    }

    NavigableListDetailPaneScaffold(
        navigator = scaffoldNavigator,
        listPane = {
            AnimatedPane {
                MessageCenterView(
                    viewModel = listViewModel,
                    messageIdToDisplay = panesViewModel.messageIdToDisplay,
                    navigateToMessageId = { messageId ->
                        scope.launch {
                            scaffoldNavigator.navigateTo(
                                ListDetailPaneScaffoldRole.Detail,
                                messageId
                            )
                        }

                        panesViewModel.resetMessageId()
                    },
                    backButtonVisibility = if (isTwoPaneMode) {
                        BackButtonVisibility.Visible({
                            messageViewModel.clearMessage()
                            listViewModel.setHighlighted(null)
                            panesViewModel.resetMessageId()

                            scope.launch {
                                scaffoldNavigator.navigateTo(
                                    ListDetailPaneScaffoldRole.Detail,
                                    null
                                )
                            }

                            onNavigateUp()
                        })
                    } else {
                        BackButtonVisibility.Hidden
                    }
                )
            }
        },
        detailPane = {
            AnimatedPane {
                MessageView(
                    messageViewModel = messageViewModel,
                    messageId = scaffoldNavigator.currentDestination?.contentKey,
                    backButtonVisibility = if (isTwoPaneMode) {
                        BackButtonVisibility.Hidden
                    } else {
                        BackButtonVisibility.Visible({
                            listViewModel.setHighlighted(null)
                            scope.launch { scaffoldNavigator.navigateBack() }
                        })
                    }
                )
            }
        }
    )
}

internal class PanesViewModel(
    inputMessageIdFlow: StateFlow<String?>,
): ViewModel() {

    private val _messageId = MutableStateFlow(inputMessageIdFlow.value)
    val messageIdToDisplay: StateFlow<String?> = _messageId.asStateFlow()

    init {
        viewModelScope.launch {
            inputMessageIdFlow.collect { value ->
                _messageId.update { value }
            }
        }
    }

    fun resetMessageId() {
        _messageId.update { null }
    }
}

public sealed class BackButtonVisibility {
    public data object Hidden: BackButtonVisibility()
    public class Visible(internal val handler: () -> Unit): BackButtonVisibility()
}
