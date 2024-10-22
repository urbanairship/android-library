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
import androidx.lifecycle.viewModelScope
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.view.MessageListView.OnShowMessageListener
import com.google.android.material.appbar.MaterialToolbar
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
    private val listToolbar: MaterialToolbar by lazy { findViewById(R.id.message_list_toolbar) }
    private val listView: MessageListView by lazy { findViewById(R.id.message_list) }
    private val messageToolbar: MaterialToolbar by lazy { findViewById(R.id.message_toolbar) }
    private val messageView: MessageView by lazy { findViewById(R.id.message_view) }

    private var viewModel: MessageCenterViewModel? = null
    private var pendingMessageId: String? = null

    init {
        inflate(context, R.layout.ua_view_message_center, this)

        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

        listToolbar.inflateMenu(R.menu.message_list_menu)
    }

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

    public fun popMessageView() {
        slidingPaneLayout.closePane()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        listToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.toggle_edit_mode -> {
                    listView.isEditing = !listView.isEditing
                    item.icon = context.getDrawable(
                        // TODO(m3-inbox): load icons from styles!
                        if (listView.isEditing) R.drawable.ic_edit_off_24 else R.drawable.ic_edit_24
                    )
                    true
                }
                R.id.refresh -> {
                    listView.refresh(animateSwipeRefresh = true)
                    true
                }
                else -> false
            }
        }

        listView.predicate = predicate

        // Listen for message clicks in the list view pane and show the message view pane
        listView.onShowMessageListener = OnShowMessageListener(::openMessagePane)

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

    private fun openMessagePane(message: Message) {
        UALog.v("openMessagePane! ${message.id}")
        messageView.messageId = message.id
        messageToolbar.title = message.title

        if (slidingPaneLayout.isSlideable) {
            messageToolbar.navigationIcon = context.getDrawable(R.drawable.ua_ic_message_center_arrow_back)
            messageToolbar.setNavigationOnClickListener {
                closeMessagePane()
            }
        } else {
            messageToolbar.navigationIcon = null
            messageToolbar.setNavigationOnClickListener(null)
        }

        slidingPaneLayout.open()
    }

    private fun closeMessagePane() {
        slidingPaneLayout.closePane()
    }

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
