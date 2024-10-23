package com.urbanairship.messagecenter.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.get
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import kotlinx.coroutines.runBlocking

/**
 * `View` that wraps a `SlidingPaneLayout` to display a [MessageListView] and [MessageView] in an
 * adaptive layout.
 *
 * On "phone-sized" screens, the message list will fill the entire view and the message
 * view will be animated in when a message is selected. On larger screens, the message list and
 * message view will be displayed side-by-side, similar to a master-detail layout.
 */
public class MessageCenterView @JvmOverloads constructor(
    private val context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyle: Int = 0
): FrameLayout(context, attrs, defStyle) {

    private val slidingPaneLayout: SlidingPaneLayout by lazy { findViewById(R.id.message_center_sliding_pane_layout) }
    private val listView: MessageListView by lazy { findViewById(R.id.message_list) }
    private val messageView: MessageView by lazy { findViewById(R.id.message_view) }

    private var viewModel: MessageCenterViewModel? = null
    private var pendingMessageId: String? = null

    init {
        inflate(context, R.layout.ua_view_message_center, this)

        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
    }

    /** Listener interface that will be called when a message should be shown or closed. */
    public interface Listener {
        public fun onShowMessage(message: Message): Boolean
        public fun onCloseMessage()
        public fun onListEditingChanged(isEditing: Boolean)
    }

    /** MessageCenterView listener. */
    public var listener: Listener? = null

    /** Optional `Predicate` to filter messages. */
    public var predicate: Predicate<Message>? = null
        set(value) {
            field = value
            listView.predicate = value
        }

    public fun displayMessage(messageId: String) {
        if (viewModel == null) {
            pendingMessageId = messageId
            return
        }

        pendingMessageId = null

        val message = runBlocking { viewModel?.getOrFetchMessage(messageId) } ?: return
        openMessagePane(message)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        listView.predicate = predicate

        // Listen for message clicks in the list view pane and show the message view pane
        listView.listener = object : MessageListView.Listener {
            override fun onShowMessage(message: Message) {
                openMessagePane(message)
            }

            override fun onEditModeChanged(isEditing: Boolean) {
                listener?.onListEditingChanged(isEditing)
            }
        }

        // Listen for back presses to close the message view pane
        findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher?.addCallback(
            SlidingPaneLayoutListener(slidingPaneLayout)
        ) ?: UALog.e {
            "No OnBackPressedDispatcherOwner found! MessageCenterView must be hosted by an " +
                    "Activity or Fragment that implements OnBackPressedDispatcherOwner."
        }

        if (viewModel == null) {
            viewModel = ViewModelProvider(
                owner = requireNotNull(findViewTreeViewModelStoreOwner()) {
                    "MessageView must be hosted in a view that has a ViewModelStoreOwner!"
                },
                factory = MessageCenterViewModel.factory()
            ).get<MessageCenterViewModel>()

            pendingMessageId?.let(::displayMessage)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewModel = null
    }

    /** Indicates whether the message list is in editing mode. */
    public val isListEditing: Boolean
        get() = listView.isEditing

    /** Sets the message list to editing mode. */
    public fun setListEditing(editing: Boolean) {
        listView.isEditing = editing
    }

    /** Refreshes the message list. */
    public fun refreshMessages(): Unit = listView.refresh(animateSwipeRefresh = true)
    
    private fun openMessagePane(message: Message) {
        messageView.messageId = message.id

        if (listener?.onShowMessage(message) == false) {
            if (isTwoPane) {
                listView.setHighlightedMessage(message)
            }
            slidingPaneLayout.open()
        }
    }

    /**
     * Closes the message view pane.
     *
     * This method will trigger a call to [Listener.onCloseMessage].
     */
    public fun closeMessagePane() {
        if (isTwoPane) {
            listView.clearHighlightedMessage()
        }
        slidingPaneLayout.closePane()
    }

    /** Indicates whether the displayed layout is a two-pane layout. */
    public val isTwoPane: Boolean
        get() = slidingPaneLayout.isSlideable.not()

    private inner class SlidingPaneLayoutListener(
        private val slidingPaneLayout: SlidingPaneLayout
    ) : OnBackPressedCallback(
        slidingPaneLayout.isSlideable && slidingPaneLayout.isOpen
    ), SlidingPaneLayout.PanelSlideListener {
        init {
            slidingPaneLayout.addPanelSlideListener(this)
        }

        override fun handleOnBackPressed() {
            // Close the message view pane on back press
           closeMessagePane()
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
}
