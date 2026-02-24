/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.view.MessageView
import com.urbanairship.messagecenter.ui.view.MessageViewModel
import com.urbanairship.messagecenter.ui.view.MessageViewState
import com.urbanairship.messagecenter.ui.view.SubscriptionCancellation
import kotlinx.coroutines.launch

/** Fragment that displays a Message Center [Message]. */
public open class MessageFragment @JvmOverloads constructor(
    @LayoutRes contentLayoutId: Int = R.layout.ua_fragment_message,
) : Fragment(contentLayoutId) {

    /** The current [Message] ID. */
    public val messageId: String? by lazy { arguments?.getString(ARG_MESSAGE_ID) }

    /** The currently displayed message, if a message is loaded. */
    public val currentMessage: Message?
        get() = viewModel.currentMessage

    /** Listener interface for `MessageFragment`. */
    public interface Listener {

        /** Called when the message is loaded. */
        public fun onMessageLoaded(message: Message)

        /** Called when the message load fails. */
        public fun onMessageLoadError(error: MessageViewState.Error.Type)

        /** Called when the message requests to be closed. */
        public fun onCloseMessage()
    }

    /** Listener for `MessageFragment` events. */
    public var listener: Listener? = null

    /** The message ViewModel. */
    protected val viewModel: MessageViewModel by viewModels { MessageViewModel.factory() }

    private var refreshSubscription: SubscriptionCancellation? = null

    /** The [MessageView] to display the message. */
    protected var messageView: MessageView? = null

    private var pendingShowEmptyView: Boolean? = null

    /** Controls whether the "No message selected" view is shown when no message is loaded. */
    public var showEmptyView: Boolean
        set(value) {
            messageView?.run {
                showEmptyView = value
                pendingShowEmptyView = null
            } ?: run {
                pendingShowEmptyView = value
            }
        }
        get() = messageView?.showEmptyView
            ?: pendingShowEmptyView
            ?: false

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val messageView = view.findViewById<MessageView>(android.R.id.message)
            .also { this.messageView = it }

        pendingShowEmptyView?.let {
            messageView.showEmptyView = it
            pendingShowEmptyView = null
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.states.collect(messageView::render)
            }
        }

        messageView.listener = object : MessageView.Listener {
            override fun onMessageLoaded(message: Message) {
                viewModel.markMessagesRead(message)

                this@MessageFragment.onMessageLoaded(message)
                listener?.onMessageLoaded(message)
            }

            override fun onRetryClicked() {
                messageId?.let { viewModel.loadMessage(it) }
            }

            override fun onMessageLoadError(error: MessageViewState.Error.Type) {
                this@MessageFragment.onMessageLoadError(error)
                listener?.onMessageLoadError(error)
            }

            override fun onCloseMessage() {
                this@MessageFragment.onCloseMessage()
                listener?.onCloseMessage()
            }
        }

        messageView.analyticsFactory = analyticsFactory@{ onDismiss ->
            val message = viewModel.currentMessage ?: return@analyticsFactory null
            viewModel.makeAnalytics(message, onDismiss)
        }

        messageView.storageFactory = { viewModel.viewStateStorage }

        if (savedInstanceState == null) {
            messageId?.let { viewModel.loadMessage(it) } ?: UALog.i {
                "MessageFragment started without a message ID. " + "Call loadMessage(messageId) to load a message."
            }
        }
    }

    override fun onResume() {
        super.onResume()
        messageView?.resumeWebView()

        refreshSubscription = viewModel.subscribeForMessageUpdates()
    }

    override fun onPause() {
        super.onPause()
        messageView?.pauseWebView()

        refreshSubscription?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        messageView?.saveWebViewState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.let { inState ->
            messageView?.restoreWebViewState(inState)
        }
    }

    /** Loads the message with the given [messageId]. */
    public fun loadMessage(messageId: String): Unit = viewModel.loadMessage(messageId)

    /** Clears the currently displayed message. */
    public fun clearMessage(): Unit {
        messageView?.onDismissed()
        viewModel.clearMessage()
    }

    /** Deletes the given [message]. */
    public fun deleteMessage(message: Message): Unit = viewModel.deleteMessages(message)

    /**
     * Called when a message is loaded.
     *
     * Subclasses can override this method to perform additional actions when a message is loaded.
     */
    protected open fun onMessageLoaded(message: Message) {}

    /**
     * Called when a message load fails.
     *
     * Subclasses can override this method to perform additional actions when a message load fails.
     */
    protected open fun onMessageLoadError(error: MessageViewState.Error.Type) {}

    /**
     * Called when a message requests to be closed.
     *
     * Subclasses can override this method to perform additional actions when a message requests to be closed.
     */
    protected open fun onCloseMessage() {}

    public companion object {
        /** Argument key to specify the Message ID to display. */
        public const val ARG_MESSAGE_ID: String = "message_id"

        /**
         * Creates a new MessageFragment
         *
         * @param messageId The Message ID to display
         * @return messageFragment new MessageFragment
         */
        @JvmStatic
        public fun newInstance(messageId: String): MessageFragment = MessageFragment()
            .apply {
            arguments = bundleOf(ARG_MESSAGE_ID to messageId)
        }
    }
}
