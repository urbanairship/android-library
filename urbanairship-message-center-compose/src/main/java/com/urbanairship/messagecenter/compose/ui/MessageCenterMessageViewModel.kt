package com.urbanairship.messagecenter.compose.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.urbanairship.UALog
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.MessageContent.WebViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal interface MessageCenterMessageViewModel {
    val states: StateFlow<State>

    val scope: CoroutineScope

    fun handle(action: Action)

    /** [MessageCenterMessage] display states. */
    sealed class State {
        /** Loading state. */
        data object Loading : State()

        /** Content state. */
        data class MessageContent(
            val message: Message,
            val content: Content,
        ) : State() {
            enum class WebViewState {
                INIT,
                LOADING,
                ERROR,
                LOADED
            }

            sealed class Content {
                data class Html(val webViewState: WebViewState = WebViewState.INIT): Content()
                data class Native(val layout: AirshipLayout): Content()
            }

            override fun toString(): String {
                return "Content(messageId=${message.id}, content=$content)"
            }
        }

        /** Error state. */
        data class Error(val error: Type) : State() {
            /** [MessageCenterMessage] error types. */
            enum class Type {
                /** Failed to fetch the message or refresh the inbox. */
                LOAD_FAILED,
                /** Message has been deleted or is expired. */
                UNAVAILABLE
            }
        }

        /** Empty state (no messages available). */
        data object Empty : State()
    }

    /** [MessageCenterMessage] UI actions. */
    sealed class Action {
        /** Action to load a message with the given [messageId]. */
        data class LoadMessage(val messageId: String) : Action()

        /** Action to clear the displayed message. */
        data object ClearMessage : Action()

        /** Action to mark the displayed message as read. */
        data object MarkCurrentMessageRead : Action()

        /** Action to delete the displayed message */
        data object DeleteCurrentMessage : Action()

        /** Action to refresh the message. */
        data object Refresh : Action()

        /** Action to update the webview state. */
        data class UpdateWebViewState(val state: WebViewState) : Action()
    }
}

/** `ViewModel` for [MessageCenterMessage]. */
internal class DefaultMessageCenterMessageViewModel(
    messageId: String? = null,
    private val inbox: Inbox = MessageCenter.shared().inbox,
) : ViewModel(), MessageCenterMessageViewModel {

    /**
     * Internal chanel for States (information consumed by the view, in order to display the message view).
     *
     * If content has been displayed, the previous content state will be restored when the view model is recreated.
     * Otherwise, the state flow will begin with [State.Loading].
     *
     * @see State
     */
    private val _states: MutableStateFlow<State> = MutableStateFlow(State.Empty)

    /**
     * A `Flow` of MessageView [States][State] (data consumed by the view in order to display the message).
     */
    override val states: StateFlow<State> = _states.asStateFlow()

    override val scope: CoroutineScope
        get() = viewModelScope

    /**
     * The currently loaded [Message], if we have one.
     *
     * This is a convenience property for accessing the message from the current state, and will
     * be `null` unless the current state is [State.MessageContent].
     */
    val currentMessage: Message?
        get() = (_states.value as? State.MessageContent)?.message

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            states.collect { state ->
                UALog.v("> $state")
            }
        }

        // Load the initial message if we have one
        messageId?.let(::loadMessage)
    }

    override fun handle(action: Action) {
        when (action) {
            is Action.LoadMessage -> loadMessage(action.messageId)
            is Action.ClearMessage -> clearMessage()
            is Action.MarkCurrentMessageRead -> markDisplayedMessageRead()
            is Action.DeleteCurrentMessage -> deleteDisplayedMessage()
            is Action.Refresh -> currentMessage?.let { loadMessage(it.id) }
            is Action.UpdateWebViewState -> updateWebViewState(action.state)
        }
    }

    /** Loads the message with the given [messageId]. */
    fun loadMessage(messageId: String) {
        if (messageId == currentMessage?.id) {
            UALog.v { "Message already loaded: $messageId" }
            return
        }

        UALog.v { "Loading message: $messageId" }

        _states.value = State.Loading

        viewModelScope.launch {
            _states.value = getOrFetchMessage(messageId)
        }

        refreshJob?.cancel()
        refreshJob = subscribeForMessageUpdates()
    }

    /** Marks the currently displayed message as read. */
    fun markDisplayedMessageRead() {
        currentMessage?.let {
            UALog.v { "Marking message read (${it.id})" }
            inbox.markMessagesRead(it.id)
        }
    }

    /** Deletes the currently displayed message. */
    fun deleteDisplayedMessage() {
        currentMessage?.let {
            UALog.v { "Deleting message: ${it.id}" }
            inbox.deleteMessages(it.id)
            clearMessage()
        }
    }

    /** Clears the currently loaded message. */
    fun clearMessage() {
        currentMessage?.let {
            UALog.v { "Clearing message: ${it.id}" }
            _states.value = State.Empty
        }
        refreshJob?.cancel()
    }

    fun refresh() {
        currentMessage?.let {
            UALog.v { "Refreshing message: ${it.id}" }
            loadMessage(it.id)
        }
    }

    fun updateWebViewState(state: WebViewState) {
        val current = _states.value
        if (current !is State.MessageContent) {
            return
        }

        val content = current.content
        if (content !is State.MessageContent.Content.Html) {
            return
        }

        UALog.v { "Updating web view state: $state" }
        _states.value = current.copy(content = content.copy(state))
    }

    fun subscribeForMessageUpdates(): Job =
        viewModelScope.launch {
            inbox.inboxUpdated.collect {
                val messageId = currentMessage?.id ?: return@collect
                _states.value = getOrFetchMessage(messageId)
            }
        }

    private suspend fun getOrFetchMessage(messageId: String): State {
        // Try to load the message from local storage
        val message = inbox.getMessage(messageId) ?: run {
            // If we don't have the message, refresh the inbox
            if (!inbox.fetchMessages()) {
                // Fetch failed, return an error
                return State.Error(State.Error.Type.LOAD_FAILED)
            }

            // Try to get the message again, now that we've refreshed
            inbox.getMessage(messageId) ?: return State.Error(State.Error.Type.UNAVAILABLE)
        }

        if (message.isExpired) {
            // Message is expired, return an error
            return State.Error(State.Error.Type.UNAVAILABLE)
        }

        return when(message.contentType) {
            Message.ContentType.HTML,
            Message.ContentType.PLAIN -> {
                val content = State.MessageContent.Content.Html(WebViewState.INIT)
                State.MessageContent(message, content)
            }

            Message.ContentType.THOMAS -> {
                val layout = inbox.loadMessageLayout(message)
                if (layout != null) {
                    val content = State.MessageContent.Content.Native(layout)
                    State.MessageContent(message, content)
                } else {
                    State.Error(State.Error.Type.UNAVAILABLE)
                }
            }
        }
    }

    companion object {
        internal val MESSAGE_ID_KEY = object : CreationExtras.Key<String?> {}

        /** Factory for creating a [MessageCenterMessageViewModel]. */
        @JvmStatic
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DefaultMessageCenterMessageViewModel(
                    messageId = this[MESSAGE_ID_KEY],
                    inbox = MessageCenter.shared().inbox,
                )
            }
        }
    }
}
