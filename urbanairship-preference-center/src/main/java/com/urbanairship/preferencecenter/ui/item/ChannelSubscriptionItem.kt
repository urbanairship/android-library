package com.urbanairship.preferencecenter.ui.item

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.ui.CommonViewHolder
import com.urbanairship.preferencecenter.util.setTextOrHide
import com.urbanairship.util.AccessibilityUtils
import com.google.android.material.switchmaterial.SwitchMaterial

internal data class ChannelSubscriptionItem(val item: Item.ChannelSubscription) :
    PrefCenterItem(TYPE_PREF_CHANNEL_SUBSCRIPTION) {

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

            bindSwitch(item)

            // Add a click listener on the whole item to provide a better experience for toggling subscriptions
            // when using screen readers.
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    switch.isChecked = !switch.isChecked
                }
            }

            updateAccessibilityDescription(
                itemView.context, item, isChecked(item.subscriptionId)
            )
        }

        internal fun bindSwitch(item: ChannelSubscriptionItem) {
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
        }

        private fun updateAccessibilityDescription(
            context: Context, item: ChannelSubscriptionItem, isChecked: Boolean
        ) {
            itemView.contentDescription = context.getString(
                com.urbanairship.preferencecenter.R.string.ua_preference_center_subscription_item_description,
                item.title,
                item.subtitle,
                if (isChecked) {
                    com.urbanairship.preferencecenter.R.string.ua_preference_center_subscribed_description
                } else {
                    com.urbanairship.preferencecenter.R.string.ua_preference_center_unsubscribed_description
                }.let(context::getString)
            )

            AccessibilityUtils.setClickActionLabel(
                itemView, if (isChecked) {
                    com.urbanairship.preferencecenter.R.string.ua_preference_center_action_unsubscribe
                } else {
                    com.urbanairship.preferencecenter.R.string.ua_preference_center_action_subscribe
                }
            )
        }
    }
}
