package com.urbanairship.messagecenter.ui.widget

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.UALog
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.ui.widget.EditableRecyclerView.Payload
import kotlinx.parcelize.Parcelize

/** Base class for a `RecyclerView` that supports editing. */
internal abstract class EditableRecyclerView<T : Parcelable, VH : EditableViewHolder<T, *>> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    /** Listener interface for `EditableRecyclerView`. */
    interface Listener<T> {
        /** Called when the edit mode is changed. */
        public fun onEditModeChanged(isEditing: Boolean)
        /** Called when the selection is changed. */
        public fun onSelectionChanged(selectedItems: List<T>, isAllSelected: Boolean)
        /** Called when an item is clicked. */
        public fun onItemClicked(item: T)
    }

    /** Listener for `EditableRecyclerView` events. */
    var listener: Listener<T>? = null

    /** Flag that controls whether the list is in editing mode. */
    var isEditing: Boolean = false
        set(value) {
            if (field != value) {
                field = value

                listener?.onEditModeChanged(value)
                editableAdapter?.run {
                    if (!value) clearSelected()
                    notifyItemRangeChanged(0, itemCount, Payload.UpdateEditing(value))
                } ?: UALog.w { "Adapter is not set!" }
            }
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

    override fun onSaveInstanceState(): Parcelable? {
        return SavedState(
            superState = super.onSaveInstanceState(),
            isEditing = isEditing,
            selectedItems = editableAdapter?.getSelected() ?: emptyList(),
            highlightedItem = editableAdapter?.getHighlighted()
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val superState: Parcelable? = if (state is SavedState<*>) {
            isEditing = state.isEditing

            editableAdapter?.restoreSelected(state.selectedItems.mapNotNull {
                @Suppress("UNCHECKED_CAST")
                it as? T
            })

            @Suppress("UNCHECKED_CAST")
            (state.highlightedItem as? T)?.let {
                editableAdapter?.setHighlighted(it)
            }

            state.superState
        } else {
            null
        }

        super.onRestoreInstanceState(superState)
    }

    /**
     * Returns a list of the currently selected items.
     *
     * @return A list of selected items.
     */
    val selectedItems: List<T>
        get() = editableAdapter?.getSelected() ?: emptyList()

    /** Returns `true` if all items are selected. */
    val isAllSelected: Boolean
        get() = editableAdapter?.isAllSelected ?: false

    /** Selects all items in the list. */
    fun selectAll() {
        editableAdapter?.selectAll()
    }

    /** Clears any selected items in the list. */
    fun clearSelected() {
        editableAdapter?.clearSelected()
    }

    /**
     * Item update payloads.
     *
     * Used to update list items without triggering a full rebind.
     */
    internal sealed class Payload {
        /** Update editing mode. */
        data class UpdateEditing(val isEditing: Boolean) : Payload()
        /** Update selected state. */
        data class UpdateSelected(val isSelected: Boolean) : Payload()
        /** Update highlighted state. */
        data class UpdateHighlighted(val isHighlighted: Boolean) : Payload()
    }

    /** Saved state for `EditableListAdapter`. */
    @Parcelize
    private data class SavedState<T : Parcelable>(
        val superState: Parcelable?,
        val isEditing: Boolean,
        val selectedItems: List<T>,
        val highlightedItem: T?
    ) : Parcelable
}

/**
 * Adapter for `EditableRecyclerView`.
 *
 * @param T The item type.
 * @param VH The view holder type, which must extend from [EditableViewHolder].
 */
internal abstract class EditableListAdapter<T, VH : EditableViewHolder<T, *>>(
    protected val listener: Listener<T>,
    protected val isEditing: () -> Boolean,
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, VH>(diffCallback) {

    private val selected: MutableSet<T> = mutableSetOf()

    /** Listener interface for `EditableListAdapter`. */
    interface Listener<T> {
        public fun onItemClicked(item: T)
        public fun onItemLongClicked(item: T)
        public fun onSelectionChanged(selectedItems: List<T>, isAllSelected: Boolean)
    }

    /** Returns `true` if all items are selected. */
    val isAllSelected: Boolean
        get() = selected.size == itemCount

    /**
     * Returns a list of the currently selected items.
     *
     * @return A list of selected items.
     */
    fun getSelected(): List<T> = selected.toList()

    /** Restores the list of [selectedItems]. */
    fun restoreSelected(selectedItems: List<T>) {
        selected.clear()
        selected.addAll(selectedItems)
        for (item in selectedItems) {
            notifyItemChanged(currentList.indexOf(item), Payload.UpdateSelected(true))
        }
        listener.onSelectionChanged(selectedItems, isAllSelected)
    }

    /** Returns `true` if the given item is selected. */
    fun isSelected(item: T): Boolean = selected.contains(item)

    /** Sets whether the given [item] is [selected][isSelected]. */
    fun setSelected(item: T, isSelected: Boolean) {
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

    /** Toggles the selected state of the given [item]. */
    fun toggleSelected(item: T): Unit = setSelected(item, !isSelected(item))

    /** Clears all selected items. */
    fun clearSelected() {
        selected.clear()
        notifyItemRangeChanged(0, itemCount, Payload.UpdateSelected(false))
        listener.onSelectionChanged(emptyList(), isAllSelected)
    }

    /** Selects all items in the list. */
    fun selectAll() {
        selected.clear()
        selected.addAll(currentList)
        notifyItemRangeChanged(0, itemCount, Payload.UpdateSelected(true))
        listener.onSelectionChanged(selected.toList(), isAllSelected)
    }

    /** Called when an [item] is clicked. */
    fun onItemClicked(item: T) {
        if (isEditing()) {
            toggleSelected(item)
        } else {
            listener.onItemClicked(item)
        }
    }

    /** Called when an [item] is long clicked. */
    fun onItemLongClicked(item: T): Boolean {
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
     * Sets the currently highlighted [item].
     *
     * This represents the selected message that is currently being displayed (if this
     * `RecyclerView` is being displayed in `MessageCenterView` in a two-pane master-detail layout).
     */
    abstract fun setHighlighted(item: T?)

    /** Returns the currently highlighted item, or `null`, if no item is highlighted. */
    abstract fun getHighlighted(): T?

    /** Clears the currently highlighted item. */
    abstract fun clearHighlighted()

    /** Returns `true` if the given [item] is highlighted. */
    abstract fun isHighlighted(item: T): Boolean

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
internal abstract class EditableViewHolder<T, V: View>(itemView: V) : RecyclerView.ViewHolder(itemView) {

    /** The editable item view. */
    internal val editableItemView: V = itemView

    /** Binds the [item] to the view. */
    internal abstract fun bind(item: T)

    /** Binds the [item] to the view with the given [payload]. */
    internal fun bind(item: T, payload: Payload) {
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
