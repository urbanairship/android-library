package com.urbanairship.messagecenter.ui.view

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** `ViewModel` for [MessageListView]. */
public class MessageListViewModel(
    private var predicate: Predicate<Message>?,
    private val inbox: Inbox = MessageCenter.shared().inbox,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // TODO(m3-inbox): save and restore state
    private val restoredState: MessageListViewState.Content? = null //savedStateHandle.get<State.Content>("state")

    /**
     * Internal chanel for States (information consumed by the view, in order to display the message list).
     *
     * If content has been displayed, the previous content state will be restored when the view model is recreated.
     * Otherwise, the state flow will begin with [MessageListViewState.Loading].
     *
     * @see MessageListViewState
     */
    private val _states: MutableStateFlow<MessageListViewState> = MutableStateFlow(restoredState ?: MessageListViewState.Loading)

    /** A `Flow` of Message List [States][MessageListViewState] (data consumed by the view in order to display the message list). */
    public val states: StateFlow<MessageListViewState> = _states.asStateFlow()

    private var refreshJob: Job? = null
    private var getMessagesJob: Job? = null

    init {
        viewModelScope.launch {
            states.collect { state ->
                UALog.v("> $state")

                // TODO(m3-inbox): save and restore state (need to make Message
                // Save state if we're showing content
                //(state as? State.Content)?.let { savedStateHandle["state"] = it }
            }
        }

        getMessages()
    }

    /** Sets the [Predicate] used to filter messages. */
    public fun setPredicate(predicate: Predicate<Message>?) {
        this.predicate = predicate
        getMessages()
    }

    /** Marks the given list of [messages] as read. */
    public fun markMessagesRead(messages: List<Message>) {
        UALog.d { "Marking ${messages.size} messages read" }
        inbox.markMessagesRead(messages.map { it.messageId }.toSet())
    }

    /** Deletes the given list of [messages]. */
    public fun deleteMessages(messages: List<Message>) {
        UALog.d { "Deleting  ${messages.size} messages" }
        inbox.deleteMessages(messages.map { it.messageId }.toSet())
    }

    /** Refreshes the inbox. */
    public fun refresh(onRefreshed: () -> Unit = {}) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            UALog.d { "Refreshing messages..." }
            inbox.fetchMessages()

            UALog.d { "Messages refreshed!" }
            onRefreshed()
        }
    }

    private fun getMessages() {
        getMessagesJob?.cancel()
        getMessagesJob = inbox.getMessagesFlow(predicate)
            .onEach { messages ->
                _states.value = MessageListViewState.Content(messages)
            }
            .catch {
                UALog.e(it) { "Error fetching messages" }
                _states.value = MessageListViewState.Error
            }
            .launchIn(viewModelScope)
    }

    public companion object {
        /**
         * Factory for [MessageListViewModel].
         *
         * @param predicate Optional `Predicate` used to filter messages.
         */
        @JvmStatic
        @JvmOverloads
        public fun factory(
            predicate: Predicate<Message>? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MessageListViewModel(
                    predicate = predicate,
                    inbox = MessageCenter.shared().inbox,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

/** [MessageListView] display states */
public sealed class MessageListViewState {
    /** Loading state. */
    public data object Loading : MessageListViewState()

    /** Content state. */
    public class Content(public val messages: List<Message>) : MessageListViewState() {

        // Avoids spamming the logs with full message payloads
        override fun toString(): String = "Content(messages=[size=${messages.size}])"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Content

            return messages == other.messages
        }

        override fun hashCode(): Int {
            return messages.hashCode()
        }
    }

    /** Error state. */
    public data object Error : MessageListViewState()
}
