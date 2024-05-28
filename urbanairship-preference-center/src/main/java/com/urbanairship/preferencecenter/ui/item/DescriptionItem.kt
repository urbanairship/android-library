package com.urbanairship.preferencecenter.ui.item

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.ui.CommonViewHolder
import com.urbanairship.preferencecenter.util.setTextOrHide
import java.util.UUID

internal data class DescriptionItem(val title: String?, val description: String?) :
    PrefCenterItem(TYPE_DESCRIPTION) {

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
            titleView.setTextOrHide(item.title)
            descriptionView.setTextOrHide(item.description)
        }
    }
}
