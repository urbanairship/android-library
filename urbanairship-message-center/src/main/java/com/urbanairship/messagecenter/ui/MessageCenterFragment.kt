package com.urbanairship.messagecenter.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.MessageCenterMessageFragment.OnMessageDeletedListener
import com.urbanairship.messagecenter.ui.MessageListFragment.OnMessageClickListener
import com.urbanairship.messagecenter.ui.view.MessageViewState
import com.urbanairship.messagecenter.util.setImportantForAccessibility
import com.google.android.material.snackbar.Snackbar
import com.urbanairship.messagecenter.core.R as CoreR

/** `Fragment` that displays the Message Center list and message view. */
public open class MessageCenterFragment(): Fragment(R.layout.ua_fragment_message_center) {

    /** Controls whether the message list is in editing mode. */
    public var isListEditing: Boolean = false
        set(value) {
            field = value
            listFragment?.isEditing = value
        }
        get() = listFragment?.isEditing ?: field

    /** Optional `Predicate` to filter the message list. */
    public var listPredicate: Predicate<Message>? = null
        set(value) {
            field = value
            listFragment?.predicate = value
        }
        get() = listFragment?.predicate ?: field

    /** Whether the message list and message view are being displayed side-by-side. */
    public val isTwoPane: Boolean
        get() = !slidingPaneLayout.isSlideable

    /** The currently displayed message, or `null` if no message is currently being displayed. */
    public val displayedMessage: Message?
        get() = messageFragment?.currentMessage

    /** Listener interface for `MessageCenterFragment` UI updates and UI interactions. */
    public interface Listener {
        /** Called when the list edit mode changes. */
        public fun onListEditModeChanged(isEditing: Boolean)
        /**
         * Called when a [message] is selected in the list for display.
         *
         * @return `true` if the message display was handled, or `false` to display the message in the default message view.
         */
        public fun onShowMessage(message: Message): Boolean
        /** Called when the loaded message is closed. */
        public fun onCloseMessage()
        /** Called when a message is loaded in the message view. */
        public fun onMessageLoaded(message: Message)
        /** Called when a message fails to load. */
        public fun onMessageLoadError(error: MessageViewState.Error.Type)
    }

    /** Listener for `MessageCenterFragment` events. */
    public var listener: Listener? = null

    /**
     * The [MessageCenterListFragment] instance, if it has been added to the view.
     *
     * This property will be `null` before `onViewCreated` is called, and after `onDestroyView` is called.
     */
    protected val listFragment: MessageCenterListFragment?
        get() = childFragmentManager.findFragmentByTag("message_list") as? MessageCenterListFragment

    /**
     * The [MessageCenterMessageFragment] instance, if it has been added to the view.
     *
     * This property will be `null` until a message is selected and displayed.
     */
    protected val messageFragment: MessageCenterMessageFragment?
        get() = childFragmentManager.findFragmentByTag("message_view") as? MessageCenterMessageFragment

    /** The top-level `SlidingPaneLayout`. */
    protected val slidingPaneLayout: SlidingPaneLayout
        get() = try {
            requireView().findViewById(R.id.message_center_sliding_pane_layout)
        } catch (e: Exception) {
            throw IllegalStateException("MessageCenterFragment requires a SlidingPaneLayout with ID R.id.message_center_sliding_pane_layout", e)
        }

    /** The message list pane container (used to host Snackbars). */
    private val listPaneContainer: CoordinatorLayout?
        get() = view?.findViewById(R.id.message_list_pane_container)

    /** The SlidingPaneLayout listener that handles back presses when the message pane is open. */
    private val slidingPaneLayoutListener: SlidingPaneLayoutListener by lazy {
        SlidingPaneLayoutListener(slidingPaneLayout)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

        // If we have a message ID argument, display it immediately.
        arguments?.getString(MESSAGE_ID)?.let(::showMessage)

        // Set up the message list pane listeners
        listFragment?.run {

            onEditListener = object : MessageListFragment.OnEditListener {
                override fun onEditModeChanged(isEditing: Boolean) {
                    listener?.onListEditModeChanged(isEditing)
                }

                override fun onDeleteMessages(count: Int) = showListSnackbar(
                    resources.getQuantityString(CoreR.plurals.ua_mc_description_deleted, count, count)
                )

                override fun onMarkMessagesRead(count: Int) = showListSnackbar(
                    resources.getQuantityString(CoreR.plurals.ua_mc_description_marked_read, count, count)
                )
            }

            onMessageClickListener = OnMessageClickListener {
                if (listener?.onShowMessage(it) != true) {
                    showMessage(it)
                } else {
                    UALog.d { "MessageCenterFragment - Message display handled by listener!" }
                }
            }
        }

        messageFragment?.run {
            onMessageDeletedListener = OnMessageDeletedListener {
                showListSnackbar(
                    resources.getQuantityString(CoreR.plurals.ua_mc_description_deleted, 1, 1)
                )
                closeMessage()
            }
        }

        // Register a callback to handle back presses when the message pane is open
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, slidingPaneLayoutListener)

        // Update the layout when the view is laid out
        view.doOnNextLayout { updateTwoPaneLayout() }
    }

    private fun showListSnackbar(message: String) {
        val view = listPaneContainer ?: listFragment?.view ?: view ?: return
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // We need to wait for the next layout to give the SlidingPaneLayout a chance to update
        view?.doOnNextLayout { updateTwoPaneLayout() }
    }

    /** Displays the given [message] in the message pane. */
    public fun showMessage(message: Message): Unit = showMessage(message.id, message.title)

    /** Displays the message with the given [messageId] in the message pane. */
    @JvmOverloads
    public fun showMessage(messageId: String, messageTitle: String? = null) {
        if (isTwoPane) {
            listFragment?.setHighlighted(messageId)
        }

       messageFragment?.run {
           setToolbarTitle(messageTitle)
           loadMessage(messageId)

           isToolbarNavIconVisible = !isTwoPane
           updatePaneAccessibility(true)

           slidingPaneLayout.open()
       }
    }

    /** Closes the currently displayed message pane. */
    public fun closeMessage() {
        if (isTwoPane) {
            messageFragment?.setToolbarTitle(null)
            messageFragment?.clearMessage()
            listFragment?.clearHighlighted()
        } else {
            updatePaneAccessibility(false)
            slidingPaneLayout.close()
        }
        listener?.onCloseMessage()
    }

    private fun updateTwoPaneLayout() {
        // Update the toolbar nav icon visibility when the configuration changes
        // We only want to show the nav icon in single pane mode
        messageFragment?.isToolbarNavIconVisible = !isTwoPane

        // Update the sliding pane layout listener so that we can intercept back presses if the
        // message pane is now open.
        slidingPaneLayoutListener.isEnabled = with (slidingPaneLayout) { isSlideable && isOpen }

        // Clear the highlighted message if we're in two-pane mode
        // This handles the case where we switch from 1- to 2-pane mode while a message is displayed
        if (!isTwoPane) {
            listFragment?.clearHighlighted()
        }

        // Update the list fragment's divider visibility
        // We only want to show the divider in two-pane mode
        listFragment?.isVerticalDividerVisible = isTwoPane
    }

    private fun updatePaneAccessibility(isShowingMessage: Boolean) {
        if (!isTwoPane) {
            // If we're only showing a single pane, we need to manage the importantForAccessibility
            // state manually, so that the message pane is accessible when it's shown. Without this,
            // the list pane will still be focused when the message pane is shown.
            listFragment?.setImportantForAccessibility(!isShowingMessage)
            messageFragment?.setImportantForAccessibility(isShowingMessage)
        } else {
            // If we're showing both panes, allow both of them to be focused.
            listFragment?.setImportantForAccessibility(true)
            messageFragment?.setImportantForAccessibility(true)
        }
    }

    private inner class SlidingPaneLayoutListener(
        layout: SlidingPaneLayout,
        enabled: Boolean = layout.isSlideable && layout.isOpen
    ) : OnBackPressedCallback(enabled), SlidingPaneLayout.PanelSlideListener {
        init {
            layout.addPanelSlideListener(this)
        }

        override fun handleOnBackPressed() {
            // Close the message view pane on back press
            closeMessage()
        }


        override fun onPanelOpened(panel: View) {
            // Allow back presses to be intercepted when the message pane is open
            isEnabled = true
        }

        override fun onPanelClosed(panel: View) {
            // Disable back presses when the message pane is closed
            isEnabled = false
        }

        override fun onPanelSlide(panel: View, slideOffset: Float) = Unit
    }

    public companion object {
        /** Argument key to specify the message */
        public const val MESSAGE_ID: String = "message_id"

        /**
         * Creates a new MessageCenterFragment
         *
         * @param messageId The message ID to display
         * @return messageFragment new [MessageCenterFragment]
         */
        @JvmStatic
        public fun newInstance(messageId: String?): MessageCenterFragment =
            MessageCenterFragment().apply {
                arguments = bundleOf(MESSAGE_ID to messageId)
            }
    }
}
