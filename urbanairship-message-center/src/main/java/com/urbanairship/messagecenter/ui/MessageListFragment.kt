package com.urbanairship.messagecenter.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.messagecenter.core.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.core.ui.view.MessageListAction
import com.urbanairship.messagecenter.core.ui.view.MessageListAction.DeleteMessages
import com.urbanairship.messagecenter.core.ui.view.MessageListAction.MarkMessagesRead
import com.urbanairship.messagecenter.core.ui.view.MessageListAction.Refresh
import com.urbanairship.messagecenter.core.ui.view.MessageListState
import com.urbanairship.messagecenter.ui.view.MessageListView
import com.urbanairship.messagecenter.core.ui.view.MessageListViewModel
import kotlinx.coroutines.launch

/** `Fragment` that displays the Message Center list and message view. */
public open class MessageListFragment @JvmOverloads constructor(
    @LayoutRes contentLayoutId: Int = R.layout.ua_fragment_message_list,
): Fragment(contentLayoutId) {

    /** The message list ViewModel. */
    protected val viewModel: MessageListViewModel by viewModels {
        MessageListViewModel.factory()
    }

    /** Flag that controls whether the list is in editing mode. */
    public var isEditing: Boolean = false
        set(value) {
            field = value
            messageListView?.isEditing = value
        }
        get() = messageListView?.isEditing ?: field

    /** Optional `Predicate` to filter messages. */
    public var predicate: Predicate<Message>? = null
        set(value) {
            field = value
            viewModel.setPredicate(value)
        }

    /** Listener interface for `MessageListFragment` message selection. */
    public fun interface OnMessageClickListener {
        /**
         * Called when a message item is selected.
         *
         * @param message The selected [Message].
         */
        public fun onMessageClick(message: Message)
    }

    /** Listener for message selection. */
    public var onMessageClickListener: OnMessageClickListener? = null

    /** Listener interface for `MessageListFragment` edit mode and editing changes. */
    public interface OnEditListener {
        /**
         * Called when the list enters or exits editing mode.
         *
         * @param isEditing `true` if the list is in editing mode, `false` otherwise.
         */
        public fun onEditModeChanged(isEditing: Boolean)

        /** Called when message items are deleted. */
        public fun onDeleteMessages(count: Int)

        /** Called when message items are marked as read. */
        public fun onMarkMessagesRead(count: Int)
    }

    /** Listener for edit mode changes. */
    public var onEditListener: OnEditListener? = null

    /** The [MessageListView], or `null` if the view has not yet been inflated. */
    protected var messageListView: MessageListView? = null

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val list: MessageListView = try {
            view.findViewById(android.R.id.list)
        } catch (e: Exception) {
            throw IllegalStateException(
                "MessageListFragment layout must include " +
                        "a MessageListView with id: android.R.id.list", e)
        }
        messageListView = list

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.states.collect { list.render(it) }
            }
        }

        list.listener = object : MessageListView.Listener {
            override fun onEditModeChanged(isEditing: Boolean) {
                this@MessageListFragment.onEditModeChanged(isEditing)

                onEditListener?.onEditModeChanged(isEditing) ?: logNullListenerWarning(
                    "OnEditModeChangedListener",
                    "onEditModeChangedListener"
                )
            }
            override fun onShowMessage(message: Message) =
                onMessageClickListener?.onMessageClick(message)
                ?: logNullListenerWarning("OnMessageSelectedListener", "onMessageClickListener")

            override fun onAction(action: MessageListAction) = when(action) {
                is DeleteMessages -> {
                    onEditListener?.onDeleteMessages(action.messages.size)
                    viewModel.deleteMessages(action.messages)
                }
                is MarkMessagesRead -> {
                    onEditListener?.onMarkMessagesRead(action.messages.size)
                    viewModel.markMessagesRead(action.messages)
                }
                is Refresh -> viewModel.refreshInbox { action.onRefreshed() }
            }
        }
    }

    /**
     * Sets the highlighted message.
     *
     * @param messageId The message ID to highlight.
     */
    public fun setHighlighted(messageId: String): Unit = viewModel.setHighlighted(messageId)

    /** Clears the highlighted message. */
    public fun clearHighlighted(): Unit = viewModel.setHighlighted(null)

    /** Deletes all messages in the list. */
    public fun deleteAllMessages() {
        (viewModel.states.value as? MessageListState.Content)?.let { state ->
            viewModel.deleteMessages(state.messages)
            onEditListener?.onDeleteMessages(state.messages.size)
        }
    }

    /** Marks all messages in the list as read. */
    public fun markAllMessagesRead() {
        (viewModel.states.value as? MessageListState.Content)?.let { state ->
            val unread = state.messages.filter { !it.isRead }
            viewModel.markMessagesRead(unread)
            onEditListener?.onMarkMessagesRead(unread.size)
        }
    }

    /** Refreshes the message list, with an optional [onRefreshed] callback. */
    public fun refresh(onRefreshed: () -> Unit = {}): Unit = viewModel.refreshInbox(onRefreshed)

    /** May be overridden in subclasses to handle edit mode changes. */
    protected open fun onEditModeChanged(isEditing: Boolean): Unit = Unit
}

private fun logNullListenerWarning(interfaceName: String, propertyName: String) = UALog.w {
    "MessageListFragment.$interfaceName is not set! Implement $interfaceName or set " +
            "MessageListFragment.$propertyName to handle message list events."
}
