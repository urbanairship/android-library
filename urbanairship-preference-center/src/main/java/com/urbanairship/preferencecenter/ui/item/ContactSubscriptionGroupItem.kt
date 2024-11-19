package com.urbanairship.preferencecenter.ui.item

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.contacts.Scope
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.ui.CommonViewHolder
import com.urbanairship.preferencecenter.util.setTextOrHide
import com.urbanairship.preferencecenter.widget.SubscriptionTypeChip
import com.google.android.material.chip.ChipGroup

internal data class ContactSubscriptionGroupItem(val item: Item.ContactSubscriptionGroup) :
    PrefCenterItem(TYPE_PREF_CONTACT_SUBSCRIPTION_GROUP) {

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

            bindChips(item)
        }

        internal fun bindChips(item: ContactSubscriptionGroupItem) {
            // Remove all chips and re-add them.
            chipGroup.removeAllViews()
            for (component in item.components) {
                SubscriptionTypeChip(itemView.context).apply {
                    text = component.display.name

                    isChecked = isChecked(item.subscriptionId, component.scopes)

                    setOnCheckedChangeListener { _, isChecked ->
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onCheckedChange(adapterPosition, component.scopes, isChecked)
                        }
                    }

                    setOnFocusChangeListener { _, focused ->
                        if (focused) {
                            this.setChipStrokeWidthResource(R.dimen.ua_preference_center_subscription_type_chip_stroke_focused_width)
                        } else {
                            this.setChipStrokeWidthResource(R.dimen.ua_preference_center_subscription_type_chip_stroke_width)
                        }
                    }

                    titleView.labelFor = this.id
                    chipGroup.addView(this, WRAP_CONTENT, WRAP_CONTENT)
                }
            }
        }
    }
}
