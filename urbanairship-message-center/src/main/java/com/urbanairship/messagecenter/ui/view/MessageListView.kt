package com.urbanairship.messagecenter.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.core.R as CoreR
import com.urbanairship.messagecenter.animator.animateFadeIn
import com.urbanairship.messagecenter.animator.animateFadeOut
import com.urbanairship.messagecenter.animator.slideInBottomAnimator
import com.urbanairship.messagecenter.animator.slideOutBottomAnimator
import com.urbanairship.messagecenter.ui.widget.EditableRecyclerView
import com.urbanairship.messagecenter.ui.widget.MessageRecyclerAdapter
import com.urbanairship.messagecenter.ui.widget.MessageRecyclerView

/** `View` that displays a list of messages. */
public class MessageListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    /** Listener interface for responding to `MessageListView` events. */
    public interface Listener {
        /** Called when the list enters or exits editing mode. */
        public fun onEditModeChanged(isEditing: Boolean)
        /** Called when a message is clicked. */
        public fun onShowMessage(message: Message)
        /** Called when the user has triggered an action in the UI. */
        public fun onAction(action: MessageListAction)
    }

    /** `MessageListView` listener. */
    public var listener: Listener? = null

    /** Flag that controls whether the list is in editing mode. */
    public var isEditing: Boolean
        set(value) = views.setEditing(value)
        get() = views.list.isEditing

    private val views: Views by lazy { Views(this) }

    init {
        inflate(context, R.layout.ua_view_message_list, this)

        with(views) {
            errorRetryButton.setOnClickListener {
                listener?.onAction(MessageListAction.Refresh())
            }

            listEditSelectAll.setOnClickListener {
                if (list.isAllSelected) {
                    list.clearSelected()
                } else {
                    list.selectAll()
                }
            }

            listEditDelete.setOnClickListener {
                listener?.onAction(MessageListAction.DeleteMessages(list.selectedItems))
                isEditing = false
            }

            listEditMarkRead.setOnClickListener {
                listener?.onAction(MessageListAction.MarkMessagesRead(list.selectedItems))
                isEditing = false
            }

            list.listener = object : EditableRecyclerView.Listener<Message, MessageRecyclerAdapter.AccessibilityAction> {
                override fun onEditModeChanged(isEditing: Boolean) {
                    listener?.onEditModeChanged(isEditing)
                    views.updateEditing(isEditing)
                }

                override fun onItemClicked(item: Message) {
                    listener?.onShowMessage(item) ?: run {
                        UALog.e { "No listener set for onShowMessage!" }
                    }
                }

                override fun onSelectionChanged(selectedItems: List<Message>, isAllSelected: Boolean) {
                    updateSelectionCount(selectedItems.size, isAllSelected)
                }

                override fun onAction(action: MessageRecyclerAdapter.AccessibilityAction, item: Message) {
                    when(action) {
                        MessageRecyclerAdapter.AccessibilityAction.MARK_READ -> MessageListAction.MarkMessagesRead(listOf(item))
                        MessageRecyclerAdapter.AccessibilityAction.DELETE -> MessageListAction.DeleteMessages(listOf(item))
                    }.let {
                        listener?.onAction(it)
                    }
                }
            }

            listRefresh.setOnRefreshListener {
                refresh { listRefresh.isRefreshing = false }
            }
        }
    }

    /** Triggers a network request to refresh the message list. */
    @JvmOverloads
    public fun refresh(animateSwipeRefresh: Boolean = false, onRefreshed: (() -> Unit)? = null) {
        if (animateSwipeRefresh) {
            views.listRefresh.isRefreshing = true
        }

        listener?.onAction(MessageListAction.Refresh {
            if (animateSwipeRefresh) {
                views.listRefresh.isRefreshing = false
            }

            onRefreshed?.invoke()
        })
    }

    /**
     * Sets the currently highlighted [message].
     *
     * This represents the selected message that is currently being displayed and may be
     * useful if this `RecyclerView` is being displayed in a two-pane master-detail layout.
     */
    public fun setHighlightedMessage(message: Message) {
        views.list.setHighlightedMessageId(message.id)
    }

    /** Clears the currently highlighted item. */
    public fun clearHighlightedMessage() {
        views.list.setHighlightedMessageId(null)
    }

    /** Renders the given [state] to the view. */
    public fun render(state: MessageListState) {
        when (state) {
            is MessageListState.Loading -> views.showLoading()
            is MessageListState.Error -> views.showError()
            is MessageListState.Content -> {
                views.list.submitList(state.messages)
                views.listRefresh.isRefreshing = state.isRefreshing

                views.list.setHighlightedMessageId(state.highlightedMessageId)

                views.showContent(isListEmpty = state.messages.isEmpty())
            }
        }
    }

    private data class Views(
        // Root view
        val view: View,
        // Child views
        val listContainer: ViewGroup = view.findViewById(R.id.list_container),
        val list: MessageRecyclerView = view.findViewById(R.id.list),
        val listRefresh: SwipeRefreshLayout = view.findViewById(R.id.list_refresh),
        val listEditingToolbar: ViewGroup = view.findViewById(R.id.edit_mode_controls),
        val listEditSelectAll: Button = listEditingToolbar.findViewById(R.id.select_all_button),
        val listEditMarkRead: Button = listEditingToolbar.findViewById(R.id.mark_read_button),
        val listEditDelete: Button = listEditingToolbar.findViewById(R.id.delete_button),
        val loading: ViewGroup = view.findViewById(R.id.loading),
        val error: ViewGroup = view.findViewById(R.id.error),
        val errorMessage: TextView = error.findViewById(R.id.error_text),
        val errorRetryButton: Button = error.findViewById(R.id.error_button),
        val empty: View = view.findViewById(R.id.list_empty)
    ) {
        private val context = view.context

        fun showContent(isListEmpty: Boolean) {
            error.visibility = View.GONE
            loading.visibility = View.GONE

            empty.isVisible = isListEmpty
            listContainer.visibility = View.VISIBLE
            listRefresh.isRefreshing = false
        }

        fun showError() {
            listContainer.visibility = View.GONE
            loading.visibility = View.GONE

            error.visibility = View.VISIBLE
        }

        fun showLoading() {
            if (listContainer.isVisible) {
                listRefresh.isRefreshing = true
            } else {
                loading.visibility = View.VISIBLE

                listContainer.visibility = View.GONE
                error.visibility = View.GONE
            }
        }

        fun setEditing(isEditing: Boolean) {
            list.isEditing = isEditing
            updateEditing(isEditing)
        }

        fun updateEditing(isEditing: Boolean) {
            if (isEditing) {
                listEditingToolbar.animateFadeIn().start()
                listEditingToolbar.slideInBottomAnimator.start()
            } else {
                listEditingToolbar.animateFadeOut().start()
                listEditingToolbar.slideOutBottomAnimator.start()
            }
        }

        fun updateSelectionCount(count: Int, isAllSelected: Boolean) {
            listEditSelectAll.text = if (isAllSelected) {
                context.getString(com.urbanairship.R.string.ua_select_none)
            } else {
                context.getString(com.urbanairship.R.string.ua_select_all)
            }
            listEditMarkRead.text = getItemLabelString(com.urbanairship.R.string.ua_mark_read, count)
            listEditDelete.text = getItemLabelString(com.urbanairship.R.string.ua_delete, count)
        }

        private fun getItemLabelString(@StringRes titleResId: Int, count: Int = 0): String =
            if (count == 0) {
                // No count, just load the title: "Mark as read", "Delete", etc.
                context.getString(titleResId)
            } else {
                // We have a count. Format the title with the count: "Mark as read (3)", "Delete (5)", etc.
                context.getString(
                    CoreR.string.ua_edit_toolbar_item_title_with_count,
                    context.getString(titleResId),
                    count
                )
            }
    }
}
