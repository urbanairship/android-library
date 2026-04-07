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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal interface MessageCenterStoriesListViewModel {

    val state: StateFlow<State>

    fun handleAction(action: Action)

    /** [MessageCenterStoriesList] display states */
    sealed class State {
        /** Loading state. */
        data object Loading : State()

        /** Error state. */
        data object Error : State()

        /** Content state. */
        data class Content(val messages: List<Message>) : State()
    }

    /** [MessageCenterStoriesList] UI actions. */
    sealed class Action {
        /** Action to refresh the message list. */
        data object Refresh : Action()
    }
}

/** `ViewModel` for [MessageCenterStoriesList]. */
internal class DefaultMessageCenterStoriesListViewModel(
    private val filterPredicate: Predicate<Message>? = null,
    private val sortComparator: Comparator<Message> = Message.SENT_DATE_COMPARATOR,
    private val inbox: Inbox = MessageCenter.shared().inbox,
) : ViewModel(), MessageCenterStoriesListViewModel {

    /**
     * Internal mutable StateFlow for state so updates can be emitted.
     */
    private val _state: MutableStateFlow<MessageCenterStoriesListViewModel.State> =
        MutableStateFlow(MessageCenterStoriesListViewModel.State.Loading)

    override val state: StateFlow<MessageCenterStoriesListViewModel.State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            inbox.getMessagesFlow(predicate = filterPredicate)
                .catch {
                    UALog.w(it) { "Error fetching messages" }
                    _state.value = MessageCenterStoriesListViewModel.State.Error
                }
                .map { it.sortedWith(sortComparator) }
                .collect { messages ->
                    _state.value = MessageCenterStoriesListViewModel.State.Content(messages)
                }
        }
    }

    override fun handleAction(action: MessageCenterStoriesListViewModel.Action) {
        when (action) {
            MessageCenterStoriesListViewModel.Action.Refresh -> refresh()
        }
    }

    /**
     * Loads messages from the inbox, applies filter predicate and sort comparator,
     * and pushes the result to state. Can be called at will to refresh content.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                val messages = inbox
                    .getMessages(filterPredicate)
                    .sortedWith(sortComparator)

                _state.value = MessageCenterStoriesListViewModel.State.Content(messages)
            } catch (e: Exception) {
                UALog.e(e) { "Error loading messages" }
                _state.value = MessageCenterStoriesListViewModel.State.Error
            }
        }
    }

    companion object {
        internal val PREDICATE_KEY = object : CreationExtras.Key<Predicate<Message>?> {}
        internal val SORT_COMPARATOR_KEY = object : CreationExtras.Key<Comparator<Message>?> {}


        /**
         * Factory for [MessageCenterStoriesListViewModel].
         *
         * @param predicate Optional `Predicate` used to filter messages.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DefaultMessageCenterStoriesListViewModel(
                    filterPredicate = this[PREDICATE_KEY],
                    sortComparator = this[SORT_COMPARATOR_KEY] ?: Message.SENT_DATE_COMPARATOR,
                    inbox = MessageCenter.shared().inbox,
                )
            }
        }
    }
}
