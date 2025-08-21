package com.urbanairship.messagecenter.ui.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.core.content.withStyledAttributes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import com.urbanairship.messagecenter.ui.widget.EditableRecyclerView.Payload
import com.urbanairship.messagecenter.ui.widget.MessageRecyclerAdapter.AccessibilityAction.DELETE
import com.urbanairship.messagecenter.ui.widget.MessageRecyclerAdapter.AccessibilityAction.MARK_READ
import com.google.android.material.divider.MaterialDividerItemDecoration

/** Base Message Center `RecyclerView` that displays a list of messages. */
internal class MessageRecyclerView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = R.style.UrbanAirship_MessageCenter_List
) : EditableRecyclerView<Message, MessageRecyclerAdapter.ViewHolder, MessageRecyclerAdapter.AccessibilityAction>(
    context,
    attrs,
    defStyleAttr
) {

    private val accessibilityManager: AccessibilityManager by lazy {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }

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

    private val accessibilityListener =
        MessageRecyclerAdapter.AccessibilityActionListener { action, message ->
            listener?.onAction(action, message)
        }

    private val adapter = MessageRecyclerAdapter(
        listener = adapterListener,
        accessibilityActionListener = accessibilityListener,
        isEditingProvider = { isEditing },
        isTouchExplorationEnabledProvider = { accessibilityManager.isTouchExplorationEnabled }
    )

    init {
        context.withStyledAttributes(
            set = attrs,
            attrs = R.styleable.UrbanAirship_MessageCenter,
            defStyleAttr = 0,
            defStyleRes = R.style.UrbanAirship_MessageCenter
        ) {
            applyDividerStyles()
        }

        addItemDecoration(VerticalSpacingItemDecoration(
            resources.getDimensionPixelSize(R.dimen.message_list_item_spacing_top),
            resources.getDimensionPixelSize(R.dimen.message_list_item_spacing_middle),
            resources.getDimensionPixelSize(R.dimen.message_list_item_spacing_bottom)
        ))

        setAdapter(adapter)
        layoutManager = LinearLayoutManager(context)

        // Rebind list items when touch exploration state changes
        accessibilityManager.addTouchExplorationStateChangeListener {
            adapter.notifyDataSetChanged()
        }
    }

    public fun submitList(messages: List<Message>): Unit = adapter.submitList(messages)

    public fun setHighlightedMessageId(messageId: String?) {
        adapter.setHighlightedItemId(messageId)
    }

    private fun TypedArray.applyDividerStyles() {
        val showDividers = getBoolean(R.styleable.UrbanAirship_MessageCenter_messageCenterItemDividersEnabled, false)

        if (showDividers) {
            val dividerDecoration = MaterialDividerItemDecoration(context, VERTICAL).apply {
                dividerInsetStart = getDimensionPixelSize(
                    R.styleable.UrbanAirship_MessageCenter_messageCenterItemDividerInsetStart,
                    context.resources.getDimensionPixelSize(R.dimen.message_item_divider_inset_start)
                )

                dividerInsetEnd = getDimensionPixelSize(
                    R.styleable.UrbanAirship_MessageCenter_messageCenterItemDividerInsetEnd,
                    context.resources.getDimensionPixelSize(R.dimen.message_item_divider_inset_end)
                )
            }
            addItemDecoration(dividerDecoration)
        }
    }
}

/** `Adapter` for displaying messages in [MessageRecyclerView]. */
internal class MessageRecyclerAdapter(
    listener: Listener<Message>,
    private val accessibilityActionListener: AccessibilityActionListener,
    isEditingProvider: () -> Boolean,
    isTouchExplorationEnabledProvider: () -> Boolean
) : EditableListAdapter<Message, MessageRecyclerAdapter.ViewHolder>(
    listener = listener,
    isEditing = isEditingProvider,
    isTouchExplorationEnabledProvider = isTouchExplorationEnabledProvider,
    diffCallback = DIFF_CALLBACK
) {

    init {
        setHasStableIds(true)
    }

    private val positionMap = mutableMapOf<String, Int>()

    private var highlightedItemId: String? = null

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun submitList(list: List<Message>?) {
        super.submitList(list)

        // Cache adapter positions for each message.
        positionMap.clear()
        list?.forEachIndexed { index, message ->
            positionMap[message.id] = index
        }
    }

    /** Accessibility actions for messages. */
    enum class AccessibilityAction {
        MARK_READ,
        DELETE
    }

    /** Listener interface for accessibility actions. */
    fun interface AccessibilityActionListener {
        fun onAccessibilityAction(action: AccessibilityAction, message: Message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        parent,
        isSelected = ::isSelected,
        isEditing = isEditing::invoke,
        isTouchExplorationEnabled = isTouchExplorationEnabledProvider::invoke,
        isHighlighted = ::isHighlighted,
        onItemClicked = ::onItemClicked,
        onItemLongClicked = ::onItemLongClicked,
        onAccessibilityAction = accessibilityActionListener::onAccessibilityAction
    )

    /**
     * Sets the currently highlighted [itemId].
     *
     * This represents the selected message that is currently being displayed (if this
     * `RecyclerView` is being displayed in `MessageCenterView` in a two-pane master-detail layout).
     */
    override fun setHighlightedItemId(itemId: String?) {
        // If the item is already highlighted, do nothing
        if (highlightedItemId == itemId) return

        highlightedItemId = itemId

        // Clear previous highlighted item
        notifyItemRangeChanged(0, currentList.size, Payload.UpdateHighlighted(false))

        // Highlight current item
        val position = positionMap[itemId] ?: -1

        if (position != -1) {
            notifyItemChanged(position, Payload.UpdateHighlighted(true))
        }
    }

    /** Returns the currently highlighted item, or `null`, if no item is highlighted. */
    override fun getHighlightedItemId(): String? = highlightedItemId

    /** Clears the currently highlighted item. */
    override fun clearHighlightedItemId() {
        highlightedItemId = null

        notifyItemRangeChanged(0, currentList.size, Payload.UpdateHighlighted(false))
    }

    /** Returns `true` if the given Message ID is highlighted. */
    override fun isHighlighted(itemId: String): Boolean =
        highlightedItemId == itemId

    /** `ViewHolder` for message items displayed in [MessageRecyclerView]. */
    public class ViewHolder(
        parent: ViewGroup,
        view: View = LayoutInflater.from(parent.context).inflate(R.layout.ua_view_message_list_item, parent, false),
        private val isSelected: (Message) -> Boolean,
        private val isEditing: () -> Boolean,
        private val isTouchExplorationEnabled: () -> Boolean,
        private val isHighlighted: (String) -> Boolean,
        private val onItemClicked: (Message) -> Unit,
        private val onItemLongClicked: (Message) -> Boolean,
        private val onAccessibilityAction: (AccessibilityAction, Message) -> Unit
    ) : EditableViewHolder<Message, MessageListItem>(itemView = view as MessageListItem) {

        /** Binds the [item] to the view. */
        override fun bind(item: Message) {
            editableItemView.run {
                bind(item)
                updateEditing(isEditing(), animate = false)
                updateSelected(isSelected(item))
                updateHighlighted(isHighlighted(item.id))

                setOnClickListener { onItemClicked(item) }

                if (!isTouchExplorationEnabled()) {
                    setOnLongClickListener { onItemLongClicked(item) }
                }

                accessibilityActionListener = object : MessageListItem.AccessibilityActionListener {
                    override fun onMarkRead(message: Message) = onAccessibilityAction(MARK_READ, message)
                    override fun onDelete(message: Message) = onAccessibilityAction(DELETE, message)
                }
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
