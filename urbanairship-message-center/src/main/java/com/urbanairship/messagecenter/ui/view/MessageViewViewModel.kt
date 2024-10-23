package com.urbanairship.messagecenter.ui.view

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.InboxListener
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.ui.view.MessageViewState.Error.Type.LOAD_FAILED
import com.urbanairship.messagecenter.ui.view.MessageViewState.Error.Type.UNAVAILABLE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** `ViewModel` for [MessageView]. */
public class MessageViewViewModel(
    private val inbox: Inbox = MessageCenter.shared().inbox,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val restoredState: MessageViewState.Content? = null //savedStateHandle.get<State.Content>("state")

    /**
     * Internal chanel for States (information consumed by the view, in order to display the message view).
     *
     * If content has been displayed, the previous content state will be restored when the view model is recreated.
     * Otherwise, the state flow will begin with [MessageViewState.Loading].
     *
     * @see MessageViewState
     */
    private val _states: MutableStateFlow<MessageViewState> = MutableStateFlow(restoredState ?: MessageViewState.Empty)

    /** The currently loaded message ID, if we have one. */
    private val currentMessageId: String?
        get() = (_states.value as? MessageViewState.Content)?.message?.messageId

    /**
     * A `Flow` of MessageView [States][MessageViewState] (data consumed by the view in order to display the message).
     */
    public val states: StateFlow<MessageViewState> = _states.asStateFlow()

    init {
        viewModelScope.launch {
            states.collect { state ->
                UALog.v("> $state")

                // Save state if we're showing content
                //(state as? State.Content)?.let { savedStateHandle["state"] = it }
            }
        }
    }

    /** Loads the message with the given [messageId]. */
    public fun loadMessage(messageId: String) {
        UALog.v { "Loading message: $messageId" }

        if (messageId == currentMessageId) {
            return
        }

        _states.value = MessageViewState.Loading

        viewModelScope.launch {
            _states.value = getOrFetchMessage(messageId)
        }
    }

    /** Clears the currently loaded message. */
    public fun clearMessage() {
        UALog.v { "Clearing message" }

        _states.value = MessageViewState.Empty
    }

    /** Marks the given list of [messages] as read. */
    public fun markMessagesRead(messages: List<Message>) {
        UALog.d { "Marking ${messages.size} messages read" }
        inbox.markMessagesRead(messages.map { it.messageId }.toSet())
    }

    internal fun subscribeForMessageUpdates(): SubscriptionCancellation {
        val listener = object : InboxListener {
            override fun onInboxUpdated() {
                val messageId = currentMessageId ?: return

                viewModelScope.launch {
                    _states.value = getOrFetchMessage(messageId)
                }
            }
        }
        inbox.addListener(listener)

        return object : SubscriptionCancellation {
            override fun cancel() = inbox.removeListener(listener)
        }
    }

    private suspend fun getOrFetchMessage(messageId: String): MessageViewState {
        // Try to load the message from local storage
        val message = inbox.getMessage(messageId) ?: run {
            // If we don't have the message, refresh the inbox
            if (!inbox.fetchMessages()) {
                // Fetch failed, return an error
                return MessageViewState.Error(LOAD_FAILED)
            }

            // Try to get the message again, now that we've refreshed
            inbox.getMessage(messageId) ?: return MessageViewState.Error(UNAVAILABLE)
        }

        if (message.isExpired) {
            // Message is expired, return an error
            return MessageViewState.Error(UNAVAILABLE)
        }

        // Message is available, return it
        return MessageViewState.Content(message)
    }

    public companion object {
        /** Factory for creating [MessageViewViewModel]. */
        @JvmStatic
        public fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MessageViewViewModel(
                    inbox = MessageCenter.shared().inbox,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

/** [MessageView] display states. */
public sealed class MessageViewState {
    /** Loading state. */
    public data object Loading : MessageViewState()

    /** Content state. */
    public data class Content(val message: Message) : MessageViewState()

    /** Error state. */
    public data class Error(val error: Type) : MessageViewState() {

        /** [MessageView] error types. */
        public enum class Type {
            /** Failed to fetch the message or refresh the inbox. */
            LOAD_FAILED,
            /** Message has been deleted or is expired. */
            UNAVAILABLE
        }
    }

    /** Empty state (no messages available). */
    public data object Empty : MessageViewState()
}

internal interface SubscriptionCancellation {
    fun cancel()
}
