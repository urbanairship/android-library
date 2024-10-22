package com.urbanairship.messagecenter.ui.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.withStyledAttributes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.google.android.material.divider.MaterialDividerItemDecoration

/** Base Message Center `RecyclerView` that displays a list of messages. */
internal class MessageRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : EditableRecyclerView<Message, MessageRecyclerAdapter.ViewHolder>(
    context,
    attrs,
    defStyleAttr
) {

    private val adapterListener = object : EditableListAdapter.Listener<Message> {
        override fun onItemClicked(item: Message) {
            if (isEditing) {
                // If we're editing, toggle the selection state
                editableAdapter?.toggleSelected(item)
            } else {
                // Otherwise, notify the listener that an item was clicked
                listener?.onItemClicked(item)
            }
        }

        override fun onItemLongClicked(item: Message) {
            // Enable edit mode if not already editing
            if (!isEditing) {
                isEditing = true
            }
        }

        override fun onSelectionChanged(selectedItems: List<Message>, isAllSelected: Boolean) {
            // Notify the listener that the selection has changed
            listener?.onSelectionChanged(selectedItems, isAllSelected)
        }
    }

    private val adapter = MessageRecyclerAdapter(
        listener = adapterListener,
        isEditingProvider = { isEditing }
    )

    init {
        context.withStyledAttributes(attrs, R.styleable.MessageCenter) {
            applyDividerStyles()

            // TODO(m3-inbox): more styling?
        }

        setAdapter(adapter)
        layoutManager = LinearLayoutManager(context)
    }

    public fun submitList(messages: List<Message>): Unit = adapter.submitList(messages)

    public fun setHighlightedMessage(message: Message) {
        adapter.setHighlighted(message)
    }

    private fun TypedArray.applyDividerStyles() {
        //TODO(m3-inbox): Fix this! it's not actually loading values from the theme attrs...
        val showDividers = getBoolean(R.styleable.MessageCenter_messageCenterDividersEnabled, false)

        if (showDividers) {
            val dividerDecoration = MaterialDividerItemDecoration(context, VERTICAL).apply {
                dividerInsetStart = getDimensionPixelSize(
                    R.styleable.MessageCenter_messageCenterDividerInsetStart,
                    context.resources.getDimensionPixelSize(R.dimen.divider_inset_start)
                )

                dividerInsetEnd = getDimensionPixelSize(
                    R.styleable.MessageCenter_messageCenterDividerInsetEnd,
                    context.resources.getDimensionPixelSize(R.dimen.divider_inset_end)
                )
            }
            addItemDecoration(dividerDecoration)
        }
    }
}

/** `Adapter` for displaying messages in [MessageRecyclerView]. */
internal class MessageRecyclerAdapter(
    listener: Listener<Message>,
    isEditingProvider: () -> Boolean,
) : EditableListAdapter<Message, MessageRecyclerAdapter.ViewHolder>(listener, isEditingProvider, DIFF_CALLBACK) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        parent,
        isSelected = ::isSelected,
        isEditing = isEditing::invoke,
        isHighlighted = ::isHighlighted,
        onItemClicked = ::onItemClicked,
        onItemLongClicked = ::onItemLongClicked,
    )

    /** `ViewHolder` for message items displayed in [MessageRecyclerView]. */
    public class ViewHolder(
        parent: ViewGroup,
        view: View = LayoutInflater.from(parent.context).inflate(R.layout.ua_view_message_list_item, parent, false),
        private val isSelected: (Message) -> Boolean,
        private val isEditing: () -> Boolean,
        private val isHighlighted: (Message) -> Boolean,
        private val onItemClicked: (Message) -> Unit,
        private val onItemLongClicked: (Message) -> Boolean,
    ) : EditableViewHolder<Message, MessageListItem>(itemView = view as MessageListItem) {

        /** Binds the [item] to the view. */
        override fun bind(item: Message) {
            editableItemView.run {
                bind(item)
                updateEditing(isEditing(), animate = false)
                updateSelected(isSelected(item))
                updateHighlighted(isHighlighted(item))

                setOnClickListener { onItemClicked(item) }
                setOnLongClickListener { onItemLongClicked(item) }
            }
        }

        /** Updates the editing state of this `item`. */
        override fun updateEditing(isEditing: Boolean): Unit =
            editableItemView.updateEditing(isEditing, animate = true)

        /** Updates the selected state of this `item`. */
        override fun updateSelected(isSelected: Boolean): Unit =
            editableItemView.updateSelected(isSelected)

        /** Updates the highlighted state of this `item`. */
        override fun updateHighlighted(isHighlighted: Boolean): Unit =
            editableItemView.updateHighlighted(isHighlighted)
    }

    internal companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean =
                oldItem == newItem
        }
    }
}
