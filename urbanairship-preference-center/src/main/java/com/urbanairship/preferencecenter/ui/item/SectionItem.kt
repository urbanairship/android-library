package com.urbanairship.preferencecenter.ui.item

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.ui.CommonViewHolder
import com.urbanairship.preferencecenter.util.setTextOrHide

internal data class SectionItem(val section: Section) :
    PrefCenterItem(TYPE_SECTION) {

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
