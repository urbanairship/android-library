package com.urbanairship.messagecenter.ui.view

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.InboxListener
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

/** `ViewModel` for [MessageView]. */
public class MessageViewModel(
    private val inbox: Inbox = MessageCenter.shared().inbox,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val restoredState: MessageViewState.Content? =
        savedStateHandle.get<MessageViewState.Content>("state")

    /**
     * Internal chanel for States (information consumed by the view, in order to display the message view).
     *
     * If content has been displayed, the previous content state will be restored when the view model is recreated.
     * Otherwise, the state flow will begin with [MessageViewState.Loading].
     *
     * @see MessageViewState
     */
    private val _states: MutableStateFlow<MessageViewState> = MutableStateFlow(restoredState ?: MessageViewState.Empty)

    /**
     * A `Flow` of MessageView [States][MessageViewState] (data consumed by the view in order to display the message).
     */
    public val states: StateFlow<MessageViewState> = _states.asStateFlow()

    /**
     * A `LiveData` of MessageView [States][MessageViewState] (data consumed by the view in order to display the message).
     */
    public val statesLiveData: LiveData<MessageViewState> = _states.asLiveData()

    /**
     * The currently loaded [Message], if we have one.
     *
     * This is a convenience property for accessing the message from the current state, and will
     * be `null` unless the current state is [MessageViewState.Content].
     */
    public val currentMessage: Message?
        get() = (_states.value as? MessageViewState.Content)?.message

    init {
        viewModelScope.launch {
            states.collect { state ->
                UALog.v("> $state")

                // Save state if we're showing content
                (state as? MessageViewState.Content)?.let { savedStateHandle["state"] = it }
            }
        }
    }

    /** Loads the message with the given [messageId]. */
    public fun loadMessage(messageId: String) {
        if (messageId == currentMessage?.id) {
            UALog.v { "Message already loaded: $messageId" }
            return
        }

        UALog.v { "Loading message: $messageId" }

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

    /** Marks the given [messages] as read. */
    public fun markMessagesRead(vararg messages: Message) {
        UALog.d { "Marking ${messages.size} messages read" }
        inbox.markMessagesRead(messages.map { it.id }.toSet())
    }

    /** Deletes the given list of [messages]. */
    public fun deleteMessages(vararg messages: Message) {
        UALog.d { "Deleting  ${messages.size} messages" }
        inbox.deleteMessages(messages.map { it.id }.toSet())
    }

    public fun subscribeForMessageUpdates(): SubscriptionCancellation {
        val job = viewModelScope.launch {
            inbox.inboxUpdated.collect {
                val messageId = currentMessage?.id ?: return@collect
                _states.value = getOrFetchMessage(messageId)
            }
        }

        return object : SubscriptionCancellation {
            override fun cancel() = job.cancel()
        }
    }

    private suspend fun getOrFetchMessage(messageId: String): MessageViewState {
        // Try to load the message from local storage
        val message = inbox.getMessage(messageId) ?: run {
            // If we don't have the message, refresh the inbox
            if (!inbox.fetchMessages()) {
                // Fetch failed, return an error
                return MessageViewState.Error(MessageViewState.Error.Type.LOAD_FAILED)
            }

            // Try to get the message again, now that we've refreshed
            inbox.getMessage(messageId) ?: return MessageViewState.Error(MessageViewState.Error.Type.UNAVAILABLE)
        }

        if (message.isExpired) {
            // Message is expired, return an error
            return MessageViewState.Error(MessageViewState.Error.Type.UNAVAILABLE)
        }

        // Message is available, return it
        return MessageViewState.Content(message)
    }

    public companion object {
        /** Factory for creating [MessageViewModel]. */
        @JvmStatic
        public fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MessageViewModel(
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
    @Parcelize
    public data class Content(
        val message: Message
    ) : MessageViewState(), Parcelable

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

/** [MessageView] UI actions. */
public sealed class MessageViewAction {
    /** The user clicked on the "retry" button in the error view. */
    public data object ErrorRetryClicked : MessageViewAction()
}

public interface SubscriptionCancellation {
    public fun cancel()
}
