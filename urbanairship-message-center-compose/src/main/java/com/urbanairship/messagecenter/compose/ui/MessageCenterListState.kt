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
import com.urbanairship.Predicate
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.State
import com.urbanairship.messagecenter.ui.view.MessageListAction

/**
 * State holder for the Message Center list screen.
 */
@Stable
public class MessageCenterListState internal constructor(
    private val onAction: (Action) -> Unit
) {
    internal var viewState by mutableStateOf<State>(State.Loading)

    /** The list of messages when in content state, or an empty list otherwise. */
    public val messages: List<Message> by derivedStateOf {
        (viewState as? State.Content)?.messages ?: emptyList()
    }

    /** Whether the list is loading for the first time. */
    public val isLoading: Boolean by derivedStateOf {
        viewState is State.Loading
    }

    /** Whether the list failed to load. */
    public val isError: Boolean by derivedStateOf {
        viewState is State.Error
    }

    /** Whether the list is refreshing while displaying content. */
    public val isRefreshing: Boolean by derivedStateOf {
        (viewState as? State.Content)?.isRefreshing == true
    }

    /** Whether the list is in editing mode. */
    public val isEditing: Boolean by derivedStateOf {
        (viewState as? State.Content)?.isEditing == true
    }

    /** The IDs of the messages selected in editing mode. */
    public val selectedMessageIds: Set<String> by derivedStateOf {
        (viewState as? State.Content)?.selectedMessageIds ?: emptySet()
    }

    internal val selectedCount: Int by derivedStateOf {
        selectedMessageIds.size
    }

    /** Whether every message in the list is selected. */
    public val areAllMessagesSelected: Boolean by derivedStateOf {
        (viewState as? State.Content)?.areAllMessagesSelected == true
    }

    /** The ID of the highlighted message, if any. */
    public val highlightedMessageId: String? by derivedStateOf {
        (viewState as? State.Content)?.highlightedMessageId
    }

    /**
     * Refreshes the message list.
     *
     * @param onRefreshed Called once the refresh completes.
     */
    public fun refresh(onRefreshed: () -> Unit = {}) {
        onAction(Action.Refresh(onRefreshed))
    }

    /** Marks the messages with the given [messageIds] as read. */
    public fun markRead(messageIds: Set<String>) {
        onAction(Action.MarkMessagesRead(messageIds))
    }

    /** Deletes the messages with the given [messageIds]. */
    public fun delete(messageIds: Set<String>) {
        onAction(Action.DeleteMessages(messageIds))
    }

    /** Marks the messages selected in editing mode as read. */
    public fun markSelectedRead() {
        onAction(Action.MarkSelectedMessagesRead)
    }

    /** Deletes the messages selected in editing mode. */
    public fun deleteSelected() {
        onAction(Action.DeleteSelectedMessages)
    }

    /** Enters or exits editing mode. Exiting clears the selection. */
    public fun setEditing(isEditing: Boolean) {
        onAction(Action.SetEditing(isEditing))
    }

    /** Selects or deselects the message with the given [messageId] in editing mode. */
    public fun setSelected(messageId: String, isSelected: Boolean) {
        onAction(Action.SetSelected(messageId, isSelected))
    }

    /** Selects every message in the list. */
    public fun selectAll() {
        onAction(Action.SelectAll)
    }

    /** Clears the selection. */
    public fun selectNone() {
        onAction(Action.SelectNone)
    }

    /** Sets the highlighted message, or clears it if [messageId] is `null`. */
    public fun setHighlighted(messageId: String?) {
        onAction(Action.SetHighlighted(messageId))
    }

    internal fun onAction(action: Action) {
        onAction.invoke(action)
    }
}

/**
 * Remembers a [MessageCenterListState].
 *
 * @param predicate An optional predicate to filter messages.
 * @param highlightedMessageId An optional message ID to highlight.
 */
@Composable
public fun rememberMessageCenterListState(
    predicate: Predicate<Message>? = null,
    highlightedMessageId: String? = null
): MessageCenterListState {
    val viewModel: DefaultMessageCenterListViewModel = viewModel(
        factory = DefaultMessageCenterListViewModel.Factory,
        extras = MutableCreationExtras().apply {
            set(DefaultMessageCenterListViewModel.PREDICATE_KEY, predicate)
            set(DefaultMessageCenterListViewModel.HIGHLIGHTED_MESSAGE_KEY, highlightedMessageId)
        }
    )

    return rememberMessageCenterListState(viewModel)
}

@Composable
internal fun rememberMessageCenterListState(
    viewModel: MessageCenterListViewModel
): MessageCenterListState {
    val state = remember { MessageCenterListState(viewModel::handle) }

    LaunchedEffect(Unit) {
        with(viewModel.scope.coroutineContext) {
            viewModel.states.collect {
                state.viewState = it
            }
        }
    }

    return state
}
