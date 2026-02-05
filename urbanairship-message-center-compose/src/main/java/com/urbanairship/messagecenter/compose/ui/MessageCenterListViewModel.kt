package com.urbanairship.messagecenter.compose.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface MessageCenterListViewModel {
    val states: StateFlow<State>

    val scope: CoroutineScope

    fun handle(action: Action)

    /** [MessageCenterList] display states */
    sealed class State {

        /** Loading state. */
        data object Loading : State()

        /** Content state. */
        data class Content(
            val messages: List<Message>,
            val isRefreshing: Boolean,
            val isEditing: Boolean = false,
            val selectedMessageIds: Set<String> = emptySet(),
            val highlightedMessageId: String? = null,
        ) : State() {

            val areAllMessagesSelected: Boolean
                get() = messages.isNotEmpty() && selectedMessageIds.size == messages.size

            fun isSelected(message: Message): Boolean = selectedMessageIds.contains(message.id)

            fun isHighlighted(message: Message): Boolean = message.id == highlightedMessageId

            // Avoids spamming the logs with full message payloads
            override fun toString(): String =
                "Content(" + "messages=[size=${messages.size}], " + "isRefreshing=$isRefreshing, " + "isEditing=$isEditing, " + "selectedMessageIds=[${selectedMessageIds.joinToString()}], " + "highlightedMessageId=$highlightedMessageId" + ")"
        }

        /** Error state. */
        data object Error : State()
    }

    /** [MessageCenterList] UI actions. */
    sealed class Action {

        /** Action to mark messages as read. */
         data class MarkMessagesRead(val messageIds: Set<String>) : Action() {
            constructor(messages: List<Message>) : this(messages.map { it.id }.toSet())
         }

        /** Action to mark the selected messages as read. */
        data object MarkSelectedMessagesRead : Action()

        /** Action to delete messages. */
        data class DeleteMessages(val messageIds: Set<String>) : Action() {
            constructor(messages: List<Message>) : this(messages.map { it.id }.toSet())
        }

        /** Action to delete the selected messages */
        data object DeleteSelectedMessages : Action()

        /** Action to refresh the message list. */
        data class Refresh(val onRefreshed: () -> Unit = {}) : Action()

        /** Action to enter or exit editing mode. */
        data class SetEditing(val isEditing: Boolean) : Action()

        /** Action to select or deselect a message. */
        data class SetSelected(val messageId: String, val isSelected: Boolean) : Action() {
            constructor(message: Message, isSelected: Boolean) : this(message.id, isSelected)
        }

        /** Action to set the highlighted message. */
        data class SetHighlighted(val messageId: String?) : Action() {
            constructor(message: Message?) : this(message?.id)
        }

        /** Action to select all messages. */
        data object SelectAll : Action()

        /** Action to clear all selected messages. */
        data object SelectNone : Action()
    }
}

/** `ViewModel` for [MessageCenterList]. */
internal class DefaultMessageCenterListViewModel(
    private val predicate: Predicate<Message>?,
    private val highlightedMessage: String? = null,
    private val inbox: Inbox = MessageCenter.shared().inbox,
) : ViewModel(), MessageCenterListViewModel {

    /**
     * Internal chanel for States (information consumed by the view, in order to display the message list).
     *
     * If content has been displayed, the previous content state will be restored when the view model is recreated.
     * Otherwise, the state flow will begin with [State.Loading].
     *
     * @see State
     */
    private val _states: MutableStateFlow<State> =
        MutableStateFlow(State.Loading)

    /** A `Flow` of Message List [States][State] (data consumed by the view in order to display the message list). */
    override val states: StateFlow<State> = _states.asStateFlow()

    override val scope: CoroutineScope
        get() = viewModelScope

    private var refreshJob: Job? = null
    private var getMessagesJob: Job? = null

    init {
        viewModelScope.launch {
            states.collect { state ->
                UALog.v("> $state")
            }
        }

        getMessages(highlightedMessage)
    }

    override fun handle(action: Action) {
        when (action) {
            is Action.DeleteMessages -> deleteMessages(action.messageIds)
            is Action.DeleteSelectedMessages -> deleteSelectedMessages()
            is Action.MarkMessagesRead -> markMessagesRead(action.messageIds)
            is Action.MarkSelectedMessagesRead -> markSelectedMessagesRead()
            is Action.Refresh -> refreshInbox()
            is Action.SelectAll -> selectAll()
            is Action.SelectNone -> selectNone()
            is Action.SetEditing -> setEditing(action.isEditing)
            is Action.SetSelected -> setSelected(action.messageId, action.isSelected)
            is Action.SetHighlighted -> setHighlighted(action.messageId)
        }
    }

    /** Sets the currently highlighted [messageId]. */
    private fun setHighlighted(messageId: String?) {
        UALog.d { "Setting highlighted message: $messageId" }
        val currentState = _states.value as? State.Content
        if (currentState != null) {
            _states.value = currentState.copy(highlightedMessageId = messageId)
        }
    }

    /** Selects all messages. */
    private fun selectAll() {
        val currentState = _states.value as? State.Content
        if (currentState != null) {
            _states.value = currentState.copy(
                selectedMessageIds = currentState.messages.map { it.id }.toSet()
            )
        }
    }

    /** Clears all selected messages. */
    private fun selectNone() {
        val currentState = _states.value as? State.Content
        if (currentState != null) {
            _states.value = currentState.copy(selectedMessageIds = emptySet())
        }
    }

    /** Enters or exits editing mode. */
    private fun setEditing(isEditing: Boolean) {
        val currentState = _states.value as? State.Content
        if (currentState != null) {
            _states.value = currentState.copy(
                isEditing = isEditing,
                // Clear selection when exiting edit mode
                selectedMessageIds = if (isEditing) currentState.selectedMessageIds else emptySet()
            )
        }
    }

    /** Marks the given list of [messages] as read. */
    private fun markMessagesRead(messages: Set<String>) {
        UALog.d { "Marking ${messages.size} messages read" }
        inbox.markMessagesRead(messages)
        setEditing(false)
    }

    /** Marks the selected messages as read. */
    private fun markSelectedMessagesRead() {
        val currentState = _states.value as? State.Content
        if (currentState != null && currentState.selectedMessageIds.isNotEmpty()) {
            markMessagesRead(currentState.selectedMessageIds)
        }
    }

    /** Deletes the given list of [messages]. */
    private fun deleteMessages(messages: Set<String>) {
        UALog.d { "Deleting ${messages.size} messages" }
        inbox.deleteMessages(messages)
        setEditing(false)
    }

    /** Deletes the selected messages. */
    private fun deleteSelectedMessages() {
        val currentState = _states.value as? State.Content
        if (currentState != null && currentState.selectedMessageIds.isNotEmpty()) {
            deleteMessages(currentState.selectedMessageIds)
        }
    }

    /** Sets the selected state of a message. */
    private fun setSelected(messageId: String, isSelected: Boolean) {
        val currentState = _states.value as? State.Content
        if (currentState != null) {
            val newSelectedMessageIds = currentState.selectedMessageIds.toMutableSet()
            if (isSelected) {
                newSelectedMessageIds.add(messageId)
            } else {
                newSelectedMessageIds.remove(messageId)
            }
            _states.value = currentState.copy(selectedMessageIds = newSelectedMessageIds)
        }
    }

    /** Refreshes the inbox. */
    private fun refreshInbox(onRefreshed: () -> Unit = {}) {
        val previouslyHighlightedMessageId = (_states.value as? State.Content)?.highlightedMessageId

        // Show progress
        if (_states.value is State.Content) {
            _states.update {
                (it as State.Content).copy(isRefreshing = true)
            }
        } else {
            _states.value = State.Loading
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            UALog.v { "Refreshing messages..." }
            inbox.fetchMessages()

            getMessages(previouslyHighlightedMessageId)
            UALog.v { "Messages refreshed!" }
            onRefreshed()
        }
    }

    private fun getMessages(
        previouslyHighlightedMessageId: String? = null
    ) {
        getMessagesJob?.cancel()
        getMessagesJob = inbox.getMessagesFlow(predicate).onEach { messages ->
                _states.update {
                    (it as? State.Content)?.copy(
                        // Update messages
                        messages = messages,
                        // Remove any selected message IDs that no longer exist
                        selectedMessageIds = it.selectedMessageIds.filter {
                            id -> messages.any { msg -> msg.id == id }
                        }.toSet(),
                        isRefreshing = false,
                        highlightedMessageId = previouslyHighlightedMessageId
                    ) ?: State.Content(
                        messages = messages,
                        isRefreshing = false,
                        highlightedMessageId = previouslyHighlightedMessageId
                    )
                }
            }.catch {
                UALog.e(it) { "Error fetching messages" }
                _states.value = State.Error
            }.launchIn(viewModelScope)
    }

    companion object {

        internal val PREDICATE_KEY = object : CreationExtras.Key<Predicate<Message>?> {}
        internal val HIGHLIGHTED_MESSAGE_KEY = object : CreationExtras.Key<String?> {}

        /**
         * Factory for [MessageCenterListViewModel].
         *
         * @param predicate Optional `Predicate` used to filter messages.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DefaultMessageCenterListViewModel(
                    predicate = this[PREDICATE_KEY],
                    highlightedMessage = this[HIGHLIGHTED_MESSAGE_KEY],
                    inbox = MessageCenter.shared().inbox,
                )
            }
        }
    }
}
