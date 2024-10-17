package com.urbanairship.messagecenter.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.UALog
import com.urbanairship.messagecenter.ui.widget.EditableRecyclerView.Payload

/** Base class for a `RecyclerView` that supports editing. */
public abstract class EditableRecyclerView<T, VH : EditableViewHolder<T, *>> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    /** Listener interface for `EditableRecyclerView`. */
    public interface Listener<T> {
        /** Called when the edit mode is changed. */
        public fun onEditModeChanged(isEditing: Boolean)
        /** Called when the selection is changed. */
        public fun onSelectionChanged(selectedItems: List<T>, isAllSelected: Boolean)
        /** Called when an item is clicked. */
        public fun onItemClicked(item: T)
    }

    /** Listener for `EditableRecyclerView` events. */
    public var listener: Listener<T>? = null

    /** Flag that controls whether the list is in editing mode. */
    public var isEditing: Boolean = false
        set(value) {
            field = value
            listener?.onEditModeChanged(value)
            editableAdapter?.run {
                if (!value) clearSelected()
                notifyItemRangeChanged(0, itemCount, Payload.UpdateEditing(value))
            } ?: UALog.w { "Adapter is not set!" }
        }

    /** Adapter for `EditableRecyclerView`. */
    protected var editableAdapter: EditableListAdapter<T, VH>? = null

    /**
     * Set the adapter for the `EditableRecyclerView`.
     *
     * @param adapter An `Adapter` that implements [EditableListAdapter].
     */
    @Throws(IllegalArgumentException::class)
    override fun setAdapter(adapter: Adapter<*>?) {
        if (isInEditMode) {
            super.setAdapter(adapter)
        } else {
            @Suppress("UNCHECKED_CAST")
            val editableAdapter = (adapter as? EditableListAdapter<T, VH>)
                ?: throw IllegalArgumentException("EditableRecyclerView requires an Adapter that extends from EditableListAdapter!")

            super.setAdapter(editableAdapter)
            this.editableAdapter = editableAdapter
        }
    }

    /**
     * Returns a list of the currently selected items.
     *
     * @return A list of selected items.
     */
    public val selectedItems: List<T>
        get() = editableAdapter?.getSelected() ?: emptyList()

    /** Returns `true` if all items are selected. */
    public val isAllSelected: Boolean
        get() = editableAdapter?.isAllSelected ?: false

    /** Selects all items in the list. */
    public fun selectAll() {
        editableAdapter?.selectAll()
    }

    /** Clears any selected items in the list. */
    public fun clearSelected() {
        editableAdapter?.clearSelected()
    }

    /**
     * Item update payloads.
     *
     * Used to update list items without triggering a full rebind.
     */
    public sealed class Payload {
        /** Update editing mode. */
        public data class UpdateEditing(val isEditing: Boolean) : Payload()
        /** Update selected state. */
        public data class UpdateSelected(val isSelected: Boolean) : Payload()
        /** Update highlighted state. */
        public data class UpdateHighlighted(val isHighlighted: Boolean) : Payload()
    }
}

/**
 * Adapter for `EditableRecyclerView`.
 *
 * @param T The item type.
 * @param VH The view holder type, which must extend from [EditableViewHolder].
 */
public abstract class EditableListAdapter<T, VH : EditableViewHolder<T, *>>(
    protected val listener: Listener<T>,
    protected val isEditing: () -> Boolean,
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, VH>(diffCallback) {

    private val selected: MutableSet<T> = mutableSetOf()

    private var highlightedItem: T? = null

    /** Listener interface for `EditableListAdapter`. */
    public interface Listener<T> {
        public fun onItemClicked(item: T)
        public fun onItemLongClicked(item: T)
        public fun onSelectionChanged(selectedItems: List<T>, isAllSelected: Boolean)
    }

    /** Returns `true` if all items are selected. */
    public val isAllSelected: Boolean
        get() = selected.size == itemCount

    /**
     * Returns a list of the currently selected items.
     *
     * @return A list of selected items.
     */
    public fun getSelected(): List<T> = selected.toList()

    /** Returns `true` if the given item is selected. */
    public fun isSelected(item: T): Boolean = selected.contains(item)

    /** Sets whether the given [item] is [selected][isSelected]. */
    public fun setSelected(item: T, isSelected: Boolean) {
        with(selected) {
            if (isSelected) {
                add(item)
            } else {
                remove(item)
            }
        }
        notifyItemChanged(currentList.indexOf(item), Payload.UpdateSelected(isSelected(item)))
        listener.onSelectionChanged(selected.toList(), isAllSelected)
    }

    /** Returns `true` if the given [item] is highlighted. */
    public fun isHighlighted(item: T): Boolean = highlightedItem == item

    /**
     * Sets the currently highlighted [item].
     *
     * This represents the selected message that is currently being displayed (if this
     * `RecyclerView` is being displayed in `MessageCenterView` in a two-pane master-detail layout).
     */
    public fun setHighlighted(item: T?) {
        if (highlightedItem == item) return

        // Clear previous highlighted item
        notifyItemChanged(currentList.indexOf(highlightedItem), Payload.UpdateHighlighted(false))

        // Highlight current item
        notifyItemChanged(currentList.indexOf(item), Payload.UpdateHighlighted(true))

        highlightedItem = item
    }

    /** Clears the currently highlighted item. */
    public fun clearHighlighted() {
        if (highlightedItem == null) return

        notifyItemChanged(currentList.indexOf(highlightedItem), Payload.UpdateHighlighted(false))

        highlightedItem = null
    }

    /** Toggles the selected state of the given [item]. */
    public fun toggleSelected(item: T): Unit = setSelected(item, !isSelected(item))

    /** Clears all selected items. */
    public fun clearSelected() {
        selected.clear()
        notifyItemRangeChanged(0, itemCount, Payload.UpdateSelected(false))
        listener.onSelectionChanged(emptyList(), isAllSelected)
    }

    /** Selects all items in the list. */
    public fun selectAll() {
        selected.clear()
        selected.addAll(currentList)
        notifyItemRangeChanged(0, itemCount, Payload.UpdateSelected(true))
        listener.onSelectionChanged(selected.toList(), isAllSelected)
    }

    /** Called when an [item] is clicked. */
    protected fun onItemClicked(item: T) {
        if (isEditing()) {
            toggleSelected(item)
        } else {
            setHighlighted(item)
            listener.onItemClicked(item)
        }
    }

    /** Called when an [item] is long clicked. */
    protected fun onItemLongClicked(item: T): Boolean {
        return if (!isEditing()) {
            setSelected(item, true)
            listener.onItemLongClicked(item)
            true
        } else {
            false
        }
    }

    /** Binds the `item` at [position] to the [ViewHolder][holder]. */
    override fun onBindViewHolder(holder: VH, position: Int) {
        onBindViewHolder(holder, position, emptyList())
    }

    /** Binds the `item` at [position] to the [ViewHolder][holder], with one or more [payloads]. */
    override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
        val item = getItem(position)
        if (payloads.isEmpty()) {
            holder.bind(item)
        } else {
            payloads.filterIsInstance<Payload>().forEach { holder.bind(item, it) }
        }
    }

    /**
     * Called to create a new `ViewHolder` for the given [viewType].
     *
     * Implementations must return a `ViewHolder` that extends from [EditableViewHolder] and holds
     * the view for the given [viewType].
     */
    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
}

/**
 * Base class for a `ViewHolder` that supports editing.
 *
 * @param T The item type.
 * @param V The view type. Must extend from an Android `View`.
 */
public abstract class EditableViewHolder<T, V: View>(itemView: V) : RecyclerView.ViewHolder(itemView) {

    /** The editable item view. */
    protected val editableItemView: V = itemView

    /** Binds the [item] to the view. */
    public abstract fun bind(item: T)

    /** Binds the [item] to the view with the given [payload]. */
    public fun bind(item: T, payload: Payload) {
        when (payload) {
            is Payload.UpdateEditing -> updateEditing(payload.isEditing)
            is Payload.UpdateSelected -> updateSelected(payload.isSelected)
            is Payload.UpdateHighlighted -> updateHighlighted(payload.isHighlighted)
        }
    }

    /** Updates the editing state of this `item`. */
    public abstract fun updateEditing(isEditing: Boolean)

    /** Updates the selected state of this `item`. */
    public abstract fun updateSelected(isSelected: Boolean)

    /** Updates the highlighted state of this `item`. */
    public abstract fun updateHighlighted(isHighlighted: Boolean)
}
