package com.urbanairship.preferencecenter.ui.item

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.ui.CommonViewHolder
import com.urbanairship.preferencecenter.util.ActionsMap
import com.urbanairship.preferencecenter.util.loadImageOrHide
import com.urbanairship.preferencecenter.util.setTextOrHide

internal data class AlertItem(val item: Item.Alert) :
    PrefCenterItem(TYPE_ALERT) {

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
        return title == otherItem.title && description == otherItem.description && icon == otherItem.icon && button == otherItem.button
    }

    class ViewHolder(
        itemView: View,
        private val onClick: (actions: ActionsMap) -> Unit
    ) : CommonViewHolder<AlertItem>(itemView) {

        private val iconView: ImageView = itemView.findViewById(R.id.ua_pref_icon)
        private val buttonView: Button = itemView.findViewById(R.id.ua_pref_button)

        override fun bind(item: AlertItem) {
            titleView.setTextOrHide(item.title)
            descriptionView.setTextOrHide(item.description)
            iconView.loadImageOrHide(item.icon)

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
