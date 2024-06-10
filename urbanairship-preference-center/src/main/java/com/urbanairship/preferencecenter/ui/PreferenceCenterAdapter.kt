package com.urbanairship.preferencecenter.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.contacts.Scope
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State.Content.ContactChannelState
import com.urbanairship.preferencecenter.ui.item.AlertItem
import com.urbanairship.preferencecenter.ui.item.ChannelSubscriptionItem
import com.urbanairship.preferencecenter.ui.item.ContactManagementItem
import com.urbanairship.preferencecenter.ui.item.ContactSubscriptionGroupItem
import com.urbanairship.preferencecenter.ui.item.ContactSubscriptionItem
import com.urbanairship.preferencecenter.ui.item.DescriptionItem
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem.Companion.TYPE_ALERT
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem.Companion.TYPE_CONTACT_MANAGEMENT
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem.Companion.TYPE_DESCRIPTION
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem.Companion.TYPE_PREF_CHANNEL_SUBSCRIPTION
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem.Companion.TYPE_PREF_CONTACT_SUBSCRIPTION
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem.Companion.TYPE_PREF_CONTACT_SUBSCRIPTION_GROUP
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem.Companion.TYPE_SECTION
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem.Companion.TYPE_SECTION_BREAK
import com.urbanairship.preferencecenter.ui.item.SectionBreakItem
import com.urbanairship.preferencecenter.ui.item.SectionItem
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

    private val channelSubscriptions: MutableSet<String> = mutableSetOf()
    private val contactSubscriptions: MutableMap<String, Set<Scope>> = mutableMapOf()
    private val contactChannels: MutableMap<ContactChannel, ContactChannelState> = mutableMapOf()

    private var descriptionItem: DescriptionItem? = null

    sealed class ItemEvent {
        data class ChannelSubscriptionChange(
            val item: Item.ChannelSubscription,
            val isChecked: Boolean
        ) : ItemEvent()

        data class ContactSubscriptionChange(
            val item: Item.ContactSubscription,
            val scopes: Set<Scope>,
            val isChecked: Boolean
        ) : ItemEvent()

        data class ContactSubscriptionGroupChange(
            val item: Item.ContactSubscriptionGroup,
            val scopes: Set<Scope>,
            val isChecked: Boolean
        ) : ItemEvent()

        data class ButtonClick(
            val actions: Map<String, JsonValue>
        ) : ItemEvent()

        data class ContactManagementAddClick(
            val item: Item.ContactManagement
        ) : ItemEvent()

        data class ContactManagementRemoveClick(
            val item: Item.ContactManagement,
            val channel: ContactChannel
        ) : ItemEvent()

        data class ContactManagementResendClick(
            val item: Item.ContactManagement,
            val channel: ContactChannel
        ) : ItemEvent()
    }

    private val itemEventsFlow: MutableSharedFlow<ItemEvent> = MutableSharedFlow()

    val itemEvents: SharedFlow<ItemEvent> = itemEventsFlow.asSharedFlow()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrefCenterViewHolder<*> = when (viewType) {
        TYPE_DESCRIPTION ->
            DescriptionItem.createViewHolder(parent)
        TYPE_SECTION ->
            SectionItem.createViewHolder(parent)
        TYPE_SECTION_BREAK ->
            SectionBreakItem.createViewHolder(parent)
        TYPE_PREF_CHANNEL_SUBSCRIPTION ->
            ChannelSubscriptionItem.createViewHolder(
                parent = parent,
                isChecked = ::isSubscribed,
                onCheckedChange = ::emitItemEvent
            )
        TYPE_PREF_CONTACT_SUBSCRIPTION ->
            ContactSubscriptionItem.createViewHolder(
                parent = parent,
                isChecked = ::isSubscribed,
                onCheckedChange = ::emitItemEvent
            )
        TYPE_PREF_CONTACT_SUBSCRIPTION_GROUP ->
            ContactSubscriptionGroupItem.createViewHolder(
                parent = parent,
                isChecked = ::isSubscribed,
                onCheckedChange = ::emitItemEvent
            )
        TYPE_ALERT ->
            AlertItem.createViewHolder(
                parent = parent,
                onClick = ::emitActions
            )
        TYPE_CONTACT_MANAGEMENT -> {
            ContactManagementItem.createViewHolder(
                parent = parent,
                contactChannelsProvider = { contactChannels },
                onAddClick = ::emitContactManagementAddEvent,
                onRemoveClick = ::emitContactManagementRemoveEvent,
                onResendClick = ::emitContactManagementResendVerificationEvent
            )
        }
        else -> throw IllegalArgumentException("Unsupported view type: $viewType")
    }

    override fun onBindViewHolder(holder: PrefCenterViewHolder<*>, position: Int) {
        holder.bindItem(getItem(position))
    }

    override fun getItemId(position: Int): Long = UUID.fromString(getItem(position).id).leastSignificantBits

    override fun getItemViewType(position: Int): Int = getItem(position).type

    fun submit(
        items: List<PrefCenterItem>,
        channelSubscriptions: Set<String>,
        contactSubscriptions: Map<String, Set<Scope>>,
        contactChannels: Map<ContactChannel, ContactChannelState>
    ) {
        setSubscriptions(
            channelSubscriptions = channelSubscriptions,
            contactSubscriptions = contactSubscriptions,
            contactChannels = contactChannels,
            notify = true
        )
        val list = descriptionItem?.let { listOf(it) + items } ?: items
        submitList(list)
    }

    @Suppress("SameParameterValue")
    private fun setSubscriptions(
        channelSubscriptions: Set<String>,
        contactSubscriptions: Map<String, Set<Scope>>,
        contactChannels: Map<ContactChannel, ContactChannelState>,
        notify: Boolean = true
    ) {
        if (this.channelSubscriptions != channelSubscriptions) {
            with(this.channelSubscriptions) {
                clear()
                addAll(channelSubscriptions)
            }

            if (notify) {
                currentList.forEachIndexed { index, item ->
                    if (item is ChannelSubscriptionItem) {
                        notifyItemChanged(index)
                    }
                }
            }
        }
        if (this.contactSubscriptions != contactSubscriptions) {
            with(this.contactSubscriptions) {
                clear()
                putAll(contactSubscriptions)
            }

            if (notify) {
                currentList.forEachIndexed { index, item ->
                    if (item is ContactSubscriptionItem || item is ContactSubscriptionGroupItem) {
                        notifyItemChanged(index)
                    }
                }
            }
        }
        if (this.contactChannels != contactChannels) {
            with(this.contactChannels) {
                clear()
                putAll(contactChannels)
            }

            if (notify) {
                currentList.forEachIndexed { index, item ->
                    if (item is ContactManagementItem) {
                        notifyItemChanged(index)
                    }
                }
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

    /** Returns true if the channel is subscribed to the given [id]. */
    private fun isSubscribed(id: String): Boolean =
        id in channelSubscriptions

    /** Returns true if the contact is subscribed to the given [id] via one or more [scopes]. */
    private fun isSubscribed(id: String, scopes: Set<Scope>): Boolean =
        contactSubscriptions[id]?.intersect(scopes).isNullOrEmpty().not()

    private fun emitItemEvent(position: Int, isChecked: Boolean) {
        val event = when (val item = getItem(position)) {
            is ChannelSubscriptionItem -> ItemEvent.ChannelSubscriptionChange(item.item, isChecked)
            else -> null
        } ?: return

        scopeProvider().launch {
            itemEventsFlow.emit(event)
        }
    }

    private fun emitItemEvent(position: Int, scopes: Set<Scope>, isChecked: Boolean) {
        val event = when (val item = getItem(position)) {
            is ContactSubscriptionItem ->
                ItemEvent.ContactSubscriptionChange(item.item, scopes, isChecked)
            is ContactSubscriptionGroupItem ->
                ItemEvent.ContactSubscriptionGroupChange(item.item, scopes, isChecked)
            else -> null
        } ?: return

        scopeProvider().launch {
            itemEventsFlow.emit(event)
        }
    }

    private fun emitActions(actions: Map<String, JsonValue>) {
        scopeProvider().launch {
            itemEventsFlow.emit(ItemEvent.ButtonClick(actions))
        }
    }

    private fun emitContactManagementAddEvent(position: Int) {
        val item = getItem(position) as? ContactManagementItem ?: return

        scopeProvider().launch {
            itemEventsFlow.emit(ItemEvent.ContactManagementAddClick(item.item))
        }
    }

    private fun emitContactManagementRemoveEvent(position: Int, channel: ContactChannel) {
        val item = getItem(position) as? ContactManagementItem ?: return

        scopeProvider().launch {
            itemEventsFlow.emit(ItemEvent.ContactManagementRemoveClick(item.item, channel))
        }
    }

    private fun emitContactManagementResendVerificationEvent(position: Int, channel: ContactChannel) {
        val item = getItem(position) as? ContactManagementItem ?: return

        scopeProvider().launch {
            itemEventsFlow.emit(ItemEvent.ContactManagementResendClick(item.item, channel))
        }
    }
}

internal abstract class PrefCenterViewHolder<T : PrefCenterItem>(itemView: View) : RecyclerView.ViewHolder(itemView) {
    @Suppress("UNCHECKED_CAST")
    fun bindItem(item: PrefCenterItem) = bind(item as T)

    abstract fun bind(item: T)
}

internal abstract class CommonViewHolder<T : PrefCenterItem>(itemView: View) : PrefCenterViewHolder<T>(itemView) {
    protected val titleView: TextView = itemView.findViewById(R.id.ua_pref_title)
    protected val descriptionView: TextView = itemView.findViewById(R.id.ua_pref_description)
}
