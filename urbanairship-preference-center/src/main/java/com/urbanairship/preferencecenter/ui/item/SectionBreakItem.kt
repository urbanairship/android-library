package com.urbanairship.preferencecenter.ui.item

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.ui.PrefCenterViewHolder
import com.google.android.material.chip.Chip

internal data class SectionBreakItem(val section: Section.SectionBreak) :
    PrefCenterItem(TYPE_SECTION_BREAK) {

    companion object {

        @LayoutRes
        val LAYOUT: Int = R.layout.ua_item_preference_section_break

        fun createViewHolder(
            parent: ViewGroup, inflater: LayoutInflater = LayoutInflater.from(parent.context)
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
