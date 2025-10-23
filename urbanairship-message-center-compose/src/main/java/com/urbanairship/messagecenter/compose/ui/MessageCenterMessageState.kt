package com.urbanairship.messagecenter.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State

/**
 * State holder for the Message Center message screen.
 */
@Stable
public class MessageCenterMessageState internal constructor(
    private val onAction: (Action) -> Unit,
) {
    internal var viewState by mutableStateOf<State>(State.Empty)

    /** The message title, or `null` if no message is displayed */
    public val title: String? by derivedStateOf {
        (viewState as? State.Content)?.message?.title
    }

    /** The message ID, or `null` if no message is displayed */
    public var messageId: String?
        get() = (viewState as? State.Content)?.message?.id
        set(_) {
            onAction(
                messageId?.let { Action.LoadMessage(it) } ?: Action.ClearMessage
            )
        }

    internal fun onAction(action: Action) {
        onAction.invoke(action)
    }
}

/**
 * Remembers a [MessageCenterMessageState].
 *
 * @param messageId An optional message ID to load.
 */
@Composable
public fun rememberMessageCenterMessageState(
    messageId: String? = null,
): MessageCenterMessageState {
    val viewModel: DefaultMessageCenterMessageViewModel = viewModel(
        factory = DefaultMessageCenterMessageViewModel.Factory,
        extras = MutableCreationExtras().apply {
            set(DefaultMessageCenterMessageViewModel.MESSAGE_ID_KEY, messageId)
        }
    )

    return rememberMessageCenterMessageState(viewModel)
}

@Composable
internal fun rememberMessageCenterMessageState(
    viewModel: MessageCenterMessageViewModel,
): MessageCenterMessageState {
    val state = remember { MessageCenterMessageState(viewModel::handle) }

    LaunchedEffect(Unit) {
        with(viewModel.scope.coroutineContext) {
            viewModel.states.collect {
                state.viewState = it
            }
        }
    }

    return state
}
