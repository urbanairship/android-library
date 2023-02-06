package com.urbanairship.preferencecenter.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.urbanairship.contacts.Scope
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.ui.PrefCenterItem.ChannelSubscriptionItem
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_ALERT
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_DESCRIPTION
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_PREF_CHANNEL_SUBSCRIPTION
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_PREF_CONTACT_SUBSCRIPTION
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_PREF_CONTACT_SUBSCRIPTION_GROUP
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_SECTION
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_SECTION_BREAK
import com.urbanairship.preferencecenter.ui.PrefCenterItem.ContactSubscriptionGroupItem
import com.urbanairship.preferencecenter.ui.PrefCenterItem.ContactSubscriptionItem
import com.urbanairship.preferencecenter.ui.PrefCenterItem.DescriptionItem
import com.urbanairship.preferencecenter.ui.PrefCenterItem.SectionBreakItem
import com.urbanairship.preferencecenter.ui.PrefCenterItem.SectionItem
import com.urbanairship.preferencecenter.util.ActionsMap
import com.urbanairship.preferencecenter.util.loadImageOrHide
import com.urbanairship.preferencecenter.util.setTextOrHide
import com.urbanairship.preferencecenter.widget.SubscriptionTypeChip
import com.urbanairship.util.AccessibilityUtils
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
            val actions: ActionsMap
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
            PrefCenterItem.AlertItem.createViewHolder(
                parent = parent,
                onClick = ::emitActions
            )
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
        contactSubscriptions: Map<String, Set<Scope>>
    ) {
        setSubscriptions(channelSubscriptions, contactSubscriptions, notify = false)
        val list = descriptionItem?.let { listOf(it) + items } ?: items
        submitList(list)
    }

    @Suppress("SameParameterValue")
    private fun setSubscriptions(
        channelSubscriptions: Set<String>,
        contactSubscriptions: Map<String, Set<Scope>>,
        notify: Boolean = true
    ) {
        var changed = false
        if (this.channelSubscriptions != channelSubscriptions) {
            with(this.channelSubscriptions) {
                clear()
                addAll(channelSubscriptions)
            }
            changed = true
        }
        if (this.contactSubscriptions != contactSubscriptions) {
            with(this.contactSubscriptions) {
                clear()
                putAll(contactSubscriptions)
            }
            changed = true
        }

        if (changed && notify) {
            notifyDataSetChanged()
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

    private fun emitActions(actions: ActionsMap) {
        scopeProvider().launch {
            itemEventsFlow.emit(ItemEvent.ButtonClick(actions))
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

internal sealed class PrefCenterItem(val type: Int) {
    companion object {
        const val TYPE_DESCRIPTION = 0
        const val TYPE_SECTION = 1
        const val TYPE_SECTION_BREAK = 2
        const val TYPE_PREF_CHANNEL_SUBSCRIPTION = 3
        const val TYPE_PREF_CONTACT_SUBSCRIPTION = 4
        const val TYPE_PREF_CONTACT_SUBSCRIPTION_GROUP = 5
        const val TYPE_ALERT = 6
    }

    abstract val id: String
    abstract val conditions: Conditions

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
        override val conditions: Conditions = emptyList()

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

        class ViewHolder(itemView: View) : CommonViewHolder<DescriptionItem>(itemView) {
            override fun bind(item: DescriptionItem) {
                titleView.visibility = View.GONE
                descriptionView.visibility = View.GONE
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
        override val conditions: Conditions = section.conditions

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

        class ViewHolder(itemView: View) : CommonViewHolder<SectionItem>(itemView) {
            override fun bind(item: SectionItem) {
                titleView.setTextOrHide(item.title)
                descriptionView.setTextOrHide(item.subtitle)
            }
        }
    }

    internal data class SectionBreakItem(val section: Section.SectionBreak) : PrefCenterItem(TYPE_SECTION_BREAK) {
        companion object {
            @LayoutRes
            val LAYOUT: Int = R.layout.ua_item_preference_section_break

            fun createViewHolder(
                parent: ViewGroup,
                inflater: LayoutInflater = LayoutInflater.from(parent.context)
            ): ViewHolder {
                val view = inflater.inflate(LAYOUT, parent, false)
                return ViewHolder(view)
            }
        }

        override val id: String = section.id
        override val conditions: Conditions = section.conditions
        val label: String? = section.display.name

        override fun areItemsTheSame(otherItem: PrefCenterItem): Boolean {
            if (this === otherItem) return true
            if (javaClass != otherItem.javaClass) return false
            otherItem as SectionBreakItem
            return id == otherItem.id
        }

        override fun areContentsTheSame(otherItem: PrefCenterItem): Boolean {
            if (javaClass != otherItem.javaClass) return false
            otherItem as SectionBreakItem
            return label == otherItem.label
        }

        class ViewHolder(itemView: View) : PrefCenterViewHolder<SectionBreakItem>(itemView) {
            private val chipView: Chip = itemView.findViewById(R.id.ua_pref_chip)

            override fun bind(item: SectionBreakItem) {
                chipView.text = item.label
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
        override val conditions: Conditions = item.conditions

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
            private val onCheckedChange: (position: Int, isChecked: Boolean) -> Unit,
        ) : CommonViewHolder<ChannelSubscriptionItem>(itemView) {
            private val switch: SwitchMaterial = itemView.findViewById(R.id.ua_pref_widget_switch)

            override fun bind(item: ChannelSubscriptionItem) {
                titleView.setTextOrHide(item.title)
                descriptionView.setTextOrHide(item.subtitle)

                with(switch) {
                    // Unset and re-set listener so that we can set up the switch without notifying listeners.
                    setOnCheckedChangeListener(null)
                    isChecked = isChecked(item.subscriptionId)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onCheckedChange(adapterPosition, isChecked)
                            updateAccessibilityDescription(itemView.context, item, isChecked)
                        }
                    }
                }

                // Add a click listener on the whole item to provide a better experience for toggling subscriptions
                // when using screen readers.
                itemView.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        switch.isChecked = !switch.isChecked
                    }
                }

                updateAccessibilityDescription(itemView.context, item, isChecked(item.subscriptionId))
            }

            private fun updateAccessibilityDescription(context: Context, item: ChannelSubscriptionItem, isChecked: Boolean) {
                itemView.contentDescription = context.getString(
                    R.string.ua_preference_center_subscription_item_description,
                    item.title,
                    item.subtitle,
                    if (isChecked) {
                        R.string.ua_preference_center_subscribed_description
                    } else {
                        R.string.ua_preference_center_unsubscribed_description
                    }.let(context::getString)
                )

                AccessibilityUtils.setClickActionLabel(itemView, if (isChecked) {
                    R.string.ua_preference_center_action_unsubscribe
                } else {
                    R.string.ua_preference_center_action_subscribe
                })
            }
        }
    }

    internal data class ContactSubscriptionItem(val item: Item.ContactSubscription) : PrefCenterItem(TYPE_PREF_CONTACT_SUBSCRIPTION) {
        companion object {
            @LayoutRes
            val LAYOUT: Int = R.layout.ua_item_preference

            @LayoutRes
            val WIDGET: Int = R.layout.ua_item_preference_widget_switch

            fun createViewHolder(
                parent: ViewGroup,
                inflater: LayoutInflater = LayoutInflater.from(parent.context),
                isChecked: (id: String, scopes: Set<Scope>) -> Boolean,
                onCheckedChange: (position: Int, scopes: Set<Scope>, isChecked: Boolean) -> Unit
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
        override val conditions: Conditions = item.conditions

        val subscriptionId: String = item.subscriptionId
        val scopes: Set<Scope> = item.scopes
        val title: String? = item.display.name
        val subtitle: String? = item.display.description

        override fun areItemsTheSame(otherItem: PrefCenterItem): Boolean {
            if (this === otherItem) return true
            if (javaClass != otherItem.javaClass) return false
            otherItem as ContactSubscriptionItem
            return id == otherItem.id
        }

        override fun areContentsTheSame(otherItem: PrefCenterItem): Boolean {
            if (javaClass != otherItem.javaClass) return false
            otherItem as ContactSubscriptionItem
            return title == otherItem.title && subtitle == otherItem.subtitle && subscriptionId == otherItem.subscriptionId &&
                scopes == otherItem.scopes
        }

        class ViewHolder(
            itemView: View,
            private val isChecked: (id: String, scopes: Set<Scope>) -> Boolean,
            private val onCheckedChange: (position: Int, scopes: Set<Scope>, isChecked: Boolean) -> Unit,
        ) : CommonViewHolder<ContactSubscriptionItem>(itemView) {
            private val switch: SwitchMaterial = itemView.findViewById(R.id.ua_pref_widget_switch)

            override fun bind(item: ContactSubscriptionItem) {
                titleView.setTextOrHide(item.title)
                descriptionView.setTextOrHide(item.subtitle)

                with(switch) {
                    // Unset and re-set listener so that we can set up the switch without notifying listeners.
                    setOnCheckedChangeListener(null)
                    isChecked = isChecked(item.subscriptionId, item.scopes)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onCheckedChange(adapterPosition, item.scopes, isChecked)
                            updateAccessibilityDescription(itemView.context, item, isChecked)
                        }
                    }
                }

                // Add a click listener on the whole item to provide a better experience for toggling subscriptions
                // when using screen readers.
                itemView.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        switch.isChecked = !switch.isChecked
                    }
                }

                updateAccessibilityDescription(itemView.context, item, isChecked(item.subscriptionId, item.scopes))
            }

            private fun updateAccessibilityDescription(context: Context, item: ContactSubscriptionItem, isChecked: Boolean) {
                itemView.contentDescription = context.getString(
                    R.string.ua_preference_center_subscription_item_description,
                    item.title,
                    item.subtitle,
                    if (isChecked) {
                        R.string.ua_preference_center_subscribed_description
                    } else {
                        R.string.ua_preference_center_unsubscribed_description
                    }.let(context::getString)
                )

                AccessibilityUtils.setClickActionLabel(itemView, if (isChecked) {
                    R.string.ua_preference_center_action_unsubscribe
                } else {
                    R.string.ua_preference_center_action_subscribe
                })
            }
        }
    }

    internal data class ContactSubscriptionGroupItem(val item: Item.ContactSubscriptionGroup) : PrefCenterItem(TYPE_PREF_CONTACT_SUBSCRIPTION_GROUP) {
        companion object {
            @LayoutRes
            val LAYOUT: Int = R.layout.ua_item_preference_contact_subscription_group

            fun createViewHolder(
                parent: ViewGroup,
                inflater: LayoutInflater = LayoutInflater.from(parent.context),
                isChecked: (id: String, scopes: Set<Scope>) -> Boolean,
                onCheckedChange: (position: Int, scopes: Set<Scope>, isChecked: Boolean) -> Unit
            ): ViewHolder {
                val view = inflater.inflate(LAYOUT, parent, false)
                return ViewHolder(view, isChecked, onCheckedChange)
            }
        }

        override val id: String = item.id
        override val conditions: Conditions = item.conditions

        val subscriptionId: String = item.subscriptionId
        val title: String? = item.display.name
        val subtitle: String? = item.display.description
        val components: List<Item.ContactSubscriptionGroup.Component> = item.components

        override fun areItemsTheSame(otherItem: PrefCenterItem): Boolean {
            if (this === otherItem) return true
            if (javaClass != otherItem.javaClass) return false
            otherItem as ContactSubscriptionGroupItem
            return id == otherItem.id
        }

        override fun areContentsTheSame(otherItem: PrefCenterItem): Boolean {
            if (javaClass != otherItem.javaClass) return false
            otherItem as ContactSubscriptionGroupItem
            return title == otherItem.title && subscriptionId == otherItem.subscriptionId && components == otherItem.components
        }

        class ViewHolder(
            itemView: View,
            private val isChecked: (id: String, scopes: Set<Scope>) -> Boolean,
            private val onCheckedChange: (position: Int, scopes: Set<Scope>, isChecked: Boolean) -> Unit,
        ) : CommonViewHolder<ContactSubscriptionGroupItem>(itemView) {
            private val chipGroup: ChipGroup = itemView.findViewById(R.id.ua_pref_chip_group)

            override fun bind(item: ContactSubscriptionGroupItem) {
                titleView.setTextOrHide(item.title)
                descriptionView.setTextOrHide(item.subtitle)

                for (component in item.components) {
                    SubscriptionTypeChip(itemView.context).apply {
                        text = component.display.name

                        isChecked = isChecked(item.subscriptionId, component.scopes)

                        setOnCheckedChangeListener { _, isChecked ->
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                onCheckedChange(adapterPosition, component.scopes, isChecked)
                            }
                        }

                        chipGroup.addView(this, WRAP_CONTENT, WRAP_CONTENT)
                    }
                }
            }
        }
    }

    internal data class AlertItem(val item: Item.Alert) : PrefCenterItem(TYPE_ALERT) {
        companion object {
             @LayoutRes
            val LAYOUT: Int = R.layout.ua_item_alert

            fun createViewHolder(
                parent: ViewGroup,
                inflater: LayoutInflater = LayoutInflater.from(parent.context),
                onClick: (actions: ActionsMap) -> Unit
            ): ViewHolder {
                val view = inflater.inflate(LAYOUT, parent, false)
                return ViewHolder(view, onClick)
            }
        }

        override val id: String = item.id
        override val conditions: Conditions = item.conditions

        val title = item.iconDisplay.name
        val description = item.iconDisplay.description
        val icon = item.iconDisplay.icon
        val button = item.button

        override fun areItemsTheSame(otherItem: PrefCenterItem): Boolean {
            if (this === otherItem) return true
            if (javaClass != otherItem.javaClass) return false
            otherItem as AlertItem
            return id == otherItem.id
        }

        override fun areContentsTheSame(otherItem: PrefCenterItem): Boolean {
            if (javaClass != otherItem.javaClass) return false
            otherItem as AlertItem
            return title == otherItem.title && description == otherItem.description &&
                    icon == otherItem.icon && button == otherItem.button
        }

        class ViewHolder(
            itemView: View,
            private val onClick: (actions: ActionsMap) -> Unit
        ) : CommonViewHolder<AlertItem>(itemView) {
            private val buttonView: Button = itemView.findViewById(R.id.ua_pref_button)

            override fun bind(item: AlertItem) {
                titleView.setTextOrHide(item.title)
                descriptionView.setTextOrHide(item.description)

                item.button?.let { button ->
                    buttonView.run {
                        text = button.text
                        contentDescription = button.contentDescription
                        isVisible = true
                        setOnClickListener { onClick(button.actions) }
                    }
                }
            }
        }
    }
}
