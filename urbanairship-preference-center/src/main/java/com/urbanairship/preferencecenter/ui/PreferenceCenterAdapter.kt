package com.urbanairship.preferencecenter.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.ui.PrefCenterItem.ChannelSubscriptionItem
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_DESCRIPTION
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_PREF_CHANNEL_SUBSCRIPTION
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_SECTION
import com.urbanairship.preferencecenter.ui.PrefCenterItem.DescriptionItem
import com.urbanairship.preferencecenter.ui.PrefCenterItem.SectionItem
import com.urbanairship.preferencecenter.util.setTextOrHide
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal class PreferenceCenterAdapter(
    private val scopeProvider: () -> CoroutineScope
) : ListAdapter<PrefCenterItem, PrefCenterViewHolder<*>>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PrefCenterItem>() {
            override fun areItemsTheSame(oldItem: PrefCenterItem, newItem: PrefCenterItem): Boolean =
                oldItem.areItemsTheSame(newItem)
            override fun areContentsTheSame(oldItem: PrefCenterItem, newItem: PrefCenterItem): Boolean =
                oldItem.areContentsTheSame(newItem)
        }
    }

    init {
        setHasStableIds(true)
    }

    private val subscriptions: MutableSet<String> = mutableSetOf()

    private var descriptionItem: DescriptionItem? = null

    sealed class ItemEvent {
        data class ChannelSubscriptionChange(
            val item: Item.ChannelSubscription,
            val isChecked: Boolean
        ) : ItemEvent()
    }

    private val itemEventsFlow: MutableSharedFlow<ItemEvent> = MutableSharedFlow()

    val itemEvents: SharedFlow<ItemEvent> = itemEventsFlow.asSharedFlow()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrefCenterViewHolder<*> = when (viewType) {
        TYPE_DESCRIPTION ->
            DescriptionItem.createViewHolder(parent)
        TYPE_SECTION ->
            SectionItem.createViewHolder(parent)
        TYPE_PREF_CHANNEL_SUBSCRIPTION ->
            ChannelSubscriptionItem.createViewHolder(
                parent = parent,
                isChecked = ::isSubscribed,
                onCheckedChange = ::emitItemEvent
            )
        else -> throw IllegalArgumentException("Unsupported view type: $viewType")
    }

    override fun onBindViewHolder(holder: PrefCenterViewHolder<*>, position: Int) {
        holder.bindItem(getItem(position))
    }

    override fun getItemId(position: Int): Long = UUID.fromString(getItem(position).id).leastSignificantBits

    override fun getItemViewType(position: Int): Int = getItem(position).type

    fun submit(items: List<PrefCenterItem>, subscriptions: Set<String>) {
        setSubscriptions(subscriptions, notify = false)
        val list = descriptionItem?.let { listOf(it) + items } ?: items
        submitList(list)
    }

    private fun setSubscriptions(subscriptions: Set<String>, notify: Boolean = true) {
        if (this.subscriptions != subscriptions) {
            with(this.subscriptions) {
                clear()
                addAll(subscriptions)
            }
            if (notify) {
                notifyDataSetChanged()
            }
        }
    }

    internal fun setHeaderItem(title: String?, description: String?) {
        val item = if (title.isNullOrBlank() && description.isNullOrBlank()) {
            null
        } else {
            DescriptionItem(title, description)
        }

        if (item != null && descriptionItem?.areContentsTheSame(item) == true) {
            // No-op. The current header item matches the one being set.
            return
        }

        descriptionItem = item

        if (currentList.isEmpty()) {
            // Nothing being displayed yet... The header will be shown when the list is submitted.
            return
        }

        val list = currentList.toMutableList()
        if (list.firstOrNull() is DescriptionItem) {
            list.removeFirst()
        }

        if (item != null) {
            list.add(0, item)
            submitList(list)
        }
    }

    private fun isSubscribed(id: String): Boolean = id in subscriptions

    private fun emitItemEvent(position: Int, isChecked: Boolean) {
        val event = when (val item = getItem(position)) {
            is ChannelSubscriptionItem -> ItemEvent.ChannelSubscriptionChange(item.item, isChecked)
            else -> null
        } ?: return

        scopeProvider().launch {
            itemEventsFlow.emit(event)
        }
    }
}

internal abstract class PrefCenterViewHolder<T : PrefCenterItem>(itemView: View) : RecyclerView.ViewHolder(itemView) {
    protected val titleView: TextView = itemView.findViewById(R.id.ua_pref_title)
    protected val descriptionView: TextView = itemView.findViewById(R.id.ua_pref_description)

    @Suppress("UNCHECKED_CAST")
    fun bindItem(item: PrefCenterItem) = bind(item as T)

    abstract fun bind(item: T)
}

internal sealed class PrefCenterItem(val type: Int) {
    companion object {
        const val TYPE_DESCRIPTION = 0
        const val TYPE_SECTION = 1
        const val TYPE_PREF_CHANNEL_SUBSCRIPTION = 2
    }

    abstract val id: String

    abstract fun areItemsTheSame(otherItem: PrefCenterItem): Boolean
    abstract fun areContentsTheSame(otherItem: PrefCenterItem): Boolean

    internal data class DescriptionItem(val title: String?, val description: String?) : PrefCenterItem(TYPE_DESCRIPTION) {
        companion object {
            @LayoutRes
            val LAYOUT: Int = R.layout.ua_item_preference_description

            fun createViewHolder(
                parent: ViewGroup,
                inflater: LayoutInflater = LayoutInflater.from(parent.context),
            ): ViewHolder {
                val view = inflater.inflate(LAYOUT, parent, false)
                return ViewHolder(view)
            }
        }

        override val id: String = UUID.randomUUID().toString()

        override fun areItemsTheSame(otherItem: PrefCenterItem): Boolean {
            if (this === otherItem) return true
            if (javaClass == otherItem.javaClass) return false
            // There should only be one description item, so two description items are always the same.
            return true
        }

        override fun areContentsTheSame(otherItem: PrefCenterItem): Boolean {
            if (javaClass != otherItem.javaClass) return false
            otherItem as DescriptionItem
            return title == otherItem.title && description == otherItem.description
        }

        class ViewHolder(itemView: View) : PrefCenterViewHolder<DescriptionItem>(itemView) {
            override fun bind(item: DescriptionItem) {
                titleView.setTextOrHide(item.title)
                descriptionView.setTextOrHide(item.description)
            }
        }
    }

    internal data class SectionItem(val section: Section) : PrefCenterItem(TYPE_SECTION) {
        companion object {
            @LayoutRes
            val LAYOUT: Int = R.layout.ua_item_preference_section

            fun createViewHolder(
                parent: ViewGroup,
                inflater: LayoutInflater = LayoutInflater.from(parent.context),
            ): ViewHolder {
                val view = inflater.inflate(LAYOUT, parent, false)
                return ViewHolder(view)
            }
        }

        override val id: String = section.id

        val title: String? = section.display.name
        val subtitle: String? = section.display.description

        override fun areItemsTheSame(otherItem: PrefCenterItem): Boolean {
            if (this === otherItem) return true
            if (javaClass != otherItem.javaClass) return false
            otherItem as SectionItem
            return id == otherItem.id
        }

        override fun areContentsTheSame(otherItem: PrefCenterItem): Boolean {
            if (javaClass != otherItem.javaClass) return false
            otherItem as SectionItem
            return title == otherItem.title && subtitle == otherItem.subtitle
        }

        class ViewHolder(itemView: View) : PrefCenterViewHolder<SectionItem>(itemView) {
            override fun bind(item: SectionItem) {
                titleView.setTextOrHide(item.title)
                descriptionView.setTextOrHide(item.subtitle)
            }
        }
    }

    internal data class ChannelSubscriptionItem(val item: Item.ChannelSubscription) : PrefCenterItem(TYPE_PREF_CHANNEL_SUBSCRIPTION) {
        companion object {
            @LayoutRes
            val LAYOUT: Int = R.layout.ua_item_preference

            @LayoutRes
            val WIDGET: Int = R.layout.ua_item_preference_widget_switch

            fun createViewHolder(
                parent: ViewGroup,
                inflater: LayoutInflater = LayoutInflater.from(parent.context),
                isChecked: (id: String) -> Boolean,
                onCheckedChange: (position: Int, isChecked: Boolean) -> Unit
            ): ViewHolder {
                val view = inflater.inflate(LAYOUT, parent, false)
                view.findViewById<LinearLayout>(R.id.ua_pref_widget)?.let { widgetRoot ->
                    inflater.inflate(WIDGET, widgetRoot)
                    widgetRoot.visibility = View.VISIBLE
                }
                return ViewHolder(view, isChecked, onCheckedChange)
            }
        }

        override val id: String = item.id

        val subscriptionId: String = item.subscriptionId
        val title: String? = item.display.name
        val subtitle: String? = item.display.description

        override fun areItemsTheSame(otherItem: PrefCenterItem): Boolean {
            if (this === otherItem) return true
            if (javaClass != otherItem.javaClass) return false
            otherItem as ChannelSubscriptionItem
            return id == otherItem.id
        }

        override fun areContentsTheSame(otherItem: PrefCenterItem): Boolean {
            if (javaClass != otherItem.javaClass) return false
            otherItem as ChannelSubscriptionItem
            return title == otherItem.title && subtitle == otherItem.subtitle && subscriptionId == otherItem.subscriptionId
        }

        class ViewHolder(
            itemView: View,
            private val isChecked: (id: String) -> Boolean,
            onCheckedChange: (position: Int, isChecked: Boolean) -> Unit,
        ) : PrefCenterViewHolder<ChannelSubscriptionItem>(itemView) {

            private val switch: SwitchMaterial = itemView.findViewById(R.id.ua_pref_widget_switch)

            private val checkedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onCheckedChange(adapterPosition, isChecked)
                }
            }

            init {
                switch.setOnCheckedChangeListener(checkedChangeListener)
            }

            override fun bind(item: ChannelSubscriptionItem) {
                titleView.setTextOrHide(item.title)
                descriptionView.setTextOrHide(item.subtitle)

                with(switch) {
                    // Unset and re-set listener so that we can set up the switch without notifying listeners.
                    setOnCheckedChangeListener(null)
                    isChecked = isChecked(item.subscriptionId)
                    setOnCheckedChangeListener(checkedChangeListener)
                }
            }
        }
    }
}
