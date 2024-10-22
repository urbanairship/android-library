package com.urbanairship.messagecenter.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.get
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.animator.animateFadeIn
import com.urbanairship.messagecenter.animator.animateFadeOut
import com.urbanairship.messagecenter.animator.slideInBottomAnimator
import com.urbanairship.messagecenter.animator.slideOutBottomAnimator
import com.urbanairship.messagecenter.ui.widget.EditableRecyclerView
import com.urbanairship.messagecenter.ui.widget.MessageRecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * `View` that displays a list of messages.
 *
 * Compared to [MessageCenterView], which wraps both the message list and message view, this view is
 * a lower level component that can be used for customizing how the message list is displayed within
 * an app's UI.
 */
public class MessageListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    /** Optional `Predicate` to filter messages. */
    public var predicate: Predicate<Message>? = null
        set(value) {
            field = value
            viewModel?.setPredicate(value)
        }

    /** Listener interface that will be called when a message is selected for display. */
    public fun interface OnShowMessageListener {
        public fun onShowMessage(message: Message)
    }

    /** Listener for showing a message. */
    public var onShowMessageListener: OnShowMessageListener? = null

    /** Flag that controls whether the list is in editing mode. */
    public var isEditing: Boolean
        set(value) = views.setEditing(value)
        get() = views.list.isEditing

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private var observeViewModelJob: Job? = null

    private var viewModel: MessageListViewModel? = null
    private val views: Views by lazy { Views(this) }

    init {
        inflate(context, R.layout.ua_view_message_list, this)

        with(views) {
            errorRetryButton.setOnClickListener {
                viewModel?.refresh()
            }

            listEditSelectAll.setOnClickListener {
                if (list.isAllSelected) {
                    list.clearSelected()
                } else {
                    list.selectAll()
                }
            }

            listEditDelete.setOnClickListener {
                viewModel?.deleteMessages(list.selectedItems)
                isEditing = false
            }

            listEditMarkRead.setOnClickListener {
                viewModel?.markMessagesRead(list.selectedItems)
                isEditing = false
            }

            list.listener = object : EditableRecyclerView.Listener<Message> {
                override fun onEditModeChanged(isEditing: Boolean) {
                    UALog.d { "onEditModeChanged: $isEditing" }
                    views.updateEditing(isEditing)
                }

                override fun onItemClicked(item: Message) {
                    UALog.d { "onItemClicked: ${item.title}" }
                    onShowMessageListener?.onShowMessage(item) ?: run {
                        // TODO: should this fall back to opening via MessageCenter.shared()?
                        UALog.w { "No listener set for onShowMessage!" }
                    }
                }

                override fun onSelectionChanged(selectedItems: List<Message>, isAllSelected: Boolean) {
                    UALog.d { "onSelectionChanged: ${selectedItems.map { it.title }}" }
                    updateSelectionCount(selectedItems.size, isAllSelected)
                }
            }

            listRefresh.setOnRefreshListener {
                refresh { listRefresh.isRefreshing = false }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (viewModel == null) {
            viewModel = ViewModelProvider(
                owner = requireNotNull(findViewTreeViewModelStoreOwner()) {
                    "MessageListView must be hosted in a view that has a ViewModelStoreOwner!"
                }, factory = MessageListViewModel.factory(predicate)
            ).get<MessageListViewModel>().also {
                viewModel = it
                observeViewModel(it)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        job.cancelChildren()

        viewModel = null
    }


    /** Triggers a network request to refresh the message list. */
    @JvmOverloads
    public fun refresh(animateSwipeRefresh: Boolean = false, onRefreshed: (() -> Unit)? = null) {
        viewModel?.refresh {
            if (animateSwipeRefresh) {
                views.listRefresh.isRefreshing = false
            }

            onRefreshed?.invoke()

            if (animateSwipeRefresh) {
                views.listRefresh.isRefreshing = true
            }
        }
    }

    public fun setHighlightedMessage(messageId: String) {
        val state = viewModel?.states?.value ?: return
        val message = when(state) {
            is MessageListViewState.Content -> state.messages.firstOrNull { it.id == messageId }
            else -> { null }
        } ?: return

        views.list.setHighlightedMessage(message)
    }

    private fun observeViewModel(viewModel: MessageListViewModel) {
        observeViewModelJob?.cancel()

        observeViewModelJob = viewModel.states
            .onEach { state ->
                when (state) {
                    is MessageListViewState.Loading -> views.showLoading()
                    is MessageListViewState.Error -> views.showError()
                    is MessageListViewState.Content -> views.list.submitList(state.messages).also {
                        UALog.d { "Submitted ${state.messages.size} messages & showing content!" }
                        views.showContent()
                    }
                }
            }
            .flowOn(Dispatchers.Main)
            .launchIn(scope)
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
    ) {
        private val context = view.context

        fun showContent() {
            error.visibility = View.GONE
            loading.visibility = View.GONE

            listContainer.visibility = View.VISIBLE
        }

        fun showError() {
            listContainer.visibility = View.GONE
            loading.visibility = View.GONE

            error.visibility = View.VISIBLE
        }

        fun showLoading() {
            listContainer.visibility = View.GONE
            error.visibility = View.GONE

            loading.visibility = View.VISIBLE
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

            if (count > 0) {
                listEditMarkRead.text = context.getString(com.urbanairship.R.string.ua_mark_read) + " ($count)"
                listEditDelete.text = context.getString(com.urbanairship.R.string.ua_delete) + " ($count)"
            } else {
                listEditMarkRead.text = context.getString(com.urbanairship.R.string.ua_mark_read)
                listEditDelete.text = context.getString(com.urbanairship.R.string.ua_delete)
            }
        }
    }
}
