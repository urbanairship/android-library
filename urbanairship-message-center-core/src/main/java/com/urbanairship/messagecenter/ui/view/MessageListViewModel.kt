package com.urbanairship.messagecenter.ui.view

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.urbanairship.Airship
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.messageCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

/** `ViewModel` for [MessageListView]. */
public class MessageListViewModel(
    private var predicate: Predicate<Message>?,
    private val inbox: Inbox = Airship.messageCenter.inbox,
//    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val restoredState: MessageListState.Content? = null
    // TODO(m3-inbox): This is causing TransactionTooLargeException... need to rethink.
//        savedStateHandle.get<MessageListState.Content>("state")

    /**
     * Internal chanel for States (information consumed by the view, in order to display the message list).
     *
     * If content has been displayed, the previous content state will be restored when the view model is recreated.
     * Otherwise, the state flow will begin with [MessageListState.Loading].
     *
     * @see MessageListState
     */
    private val _states: MutableStateFlow<MessageListState> = MutableStateFlow( restoredState ?: MessageListState.Loading)

    /** A `Flow` of Message List [States][MessageListState] (data consumed by the view in order to display the message list). */
    public val states: StateFlow<MessageListState> = _states.asStateFlow()

    /** A `LiveData` of Message  List [States][MessageListState] (data consumed by the view in order to display the message list). */
    public val statesLiveData: LiveData<MessageListState> = _states.asLiveData()

    private var refreshJob: Job? = null
    private var getMessagesJob: Job? = null

    init {
        viewModelScope.launch {
            states.collect { state ->
                UALog.v("> $state")

                // Save state if we're showing content
                // TODO(m3-inbox): This is causing TransactionTooLargeException... need to rethink.
                //(state as? MessageListState.Content)?.let { savedStateHandle["state"] = it }
            }
        }

        getMessages()
    }

    /** Sets the [Predicate] used to filter messages. */
    public fun setPredicate(predicate: Predicate<Message>?) {
        this.predicate = predicate
        getMessages()
    }

    /** Sets the currently highlighted [message]. */
    public fun setHighlighted(message: Message): Unit = setHighlighted(message.id)

    /** Sets the currently highlighted [messageId]. */
    public fun setHighlighted(messageId: String?) {
        UALog.d { "Setting highlighted message: $messageId" }
        val currentState = _states.value as? MessageListState.Content
        if (currentState != null) {
            _states.value = currentState.copy(highlightedMessageId = messageId)
        }
    }

    /** Marks the given list of [messages] as read. */
    public fun markMessagesRead(messages: List<Message>) {
        UALog.d { "Marking ${messages.size} messages read" }
        inbox.markMessagesRead(messages.map { it.id }.toSet())
    }

    /** Deletes the given list of [messages]. */
    public fun deleteMessages(messages: List<Message>) {
        UALog.d { "Deleting  ${messages.size} messages" }
        inbox.deleteMessages(messages.map { it.id }.toSet())
    }

    /** Refreshes the inbox. */
    public fun refreshInbox(onRefreshed: () -> Unit = {}) {
        // Show progress
        _states.value = MessageListState.Loading

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            UALog.d { "Refreshing messages..." }
            inbox.fetchMessages()

            getMessages()
            UALog.d { "Messages refreshed!" }
            onRefreshed()
        }
    }

    private fun getMessages() {
        getMessagesJob?.cancel()
        getMessagesJob = inbox.getMessagesFlow(predicate)
            .onEach { messages ->
                _states.value = (_states.value as? MessageListState.Content)
                    ?.copy(messages = messages)
                    ?: MessageListState.Content(messages, isRefreshing = false)
            }
            .catch {
                UALog.e(it) { "Error fetching messages" }
                _states.value = MessageListState.Error
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
                    inbox = Airship.messageCenter.inbox,
//                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

/** [MessageListView] display states */
public sealed class MessageListState {
    /** Loading state. */
    public data object Loading : MessageListState()

    /** Content state. */
    @Parcelize
    public data class Content(
        public val messages: List<Message>,
        public val isRefreshing: Boolean,
        public val highlightedMessageId: String? = null,
    ) : MessageListState(), Parcelable {

        // Avoids spamming the logs with full message payloads
        override fun toString(): String = "Content(" +
                "messages=[size=${messages.size}], " +
                "isRefreshing=$isRefreshing, " +
                "highlightedMessageId=$highlightedMessageId" +
                ")"
    }

    /** Error state. */
    public data object Error : MessageListState()
}

/** [MessageListView] UI actions. */
public sealed class MessageListAction {
    /** Action to mark messages as read. */
    public data class MarkMessagesRead(public val messages: List<Message>) : MessageListAction()

    /** Action to delete messages. */
    public data class DeleteMessages(public val messages: List<Message>) : MessageListAction()

    /** Action to refresh the message list. */
    public data class Refresh(public val onRefreshed: () -> Unit = {}) : MessageListAction()
}
