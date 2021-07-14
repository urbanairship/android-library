package com.urbanairship.preferencecenter.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.urbanairship.Logger
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_PREF_CHANNEL_SUBSCRIPTION
import com.urbanairship.preferencecenter.ui.PrefCenterItem.Companion.TYPE_SECTION
import com.urbanairship.preferencecenter.util.setTextOrHide
import java.util.UUID

internal class PreferenceCenterAdapter : ListAdapter<PrefCenterItem, PrefCenterViewHolder<*>>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PrefCenterViewHolder.create(parent, viewType)

    override fun onBindViewHolder(holder: PrefCenterViewHolder<*>, position: Int) =
        holder.bindItem(getItem(position))

    override fun getItemId(position: Int): Long =
        UUID.fromString(getItem(position).id).leastSignificantBits

    override fun getItemViewType(position: Int): Int =
        getItem(position).type

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PrefCenterItem>() {
            override fun areItemsTheSame(oldItem: PrefCenterItem, newItem: PrefCenterItem): Boolean =
                    oldItem.areItemsTheSame(newItem)
            override fun areContentsTheSame(oldItem: PrefCenterItem, newItem: PrefCenterItem): Boolean =
                    oldItem.areContentsTheSame(newItem)
        }
    }
}

internal abstract class PrefCenterViewHolder<in T : PrefCenterItem>(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {
    companion object {
        fun create(parent: ViewGroup, viewType: Int): PrefCenterViewHolder<*> = when (viewType) {
            TYPE_SECTION -> SectionItem.createViewHolder(parent)
            TYPE_PREF_CHANNEL_SUBSCRIPTION -> ChannelSubscriptionItem.createViewHolder(parent)
            else -> throw IllegalArgumentException("Unsupported view type: $viewType")
        }
    }

    protected val titleView: TextView = itemView.findViewById(R.id.ua_pref_title)
    protected val subtitleView: TextView = itemView.findViewById(R.id.ua_pref_description)

    @Suppress("UNCHECKED_CAST")
    fun bindItem(item: PrefCenterItem) = bind(item as T)

    abstract fun bind(item: T)
}

internal sealed class PrefCenterItem(val type: Int) {
    companion object {
        const val TYPE_SECTION = 0
        const val TYPE_PREF_CHANNEL_SUBSCRIPTION = 1
    }

    abstract val id: String

    abstract fun areItemsTheSame(otherItem: PrefCenterItem): Boolean
    abstract fun areContentsTheSame(otherItem: PrefCenterItem): Boolean
}

internal data class SectionItem(
    override val id: String,
    val title: String?,
    val subtitle: String?,
) : PrefCenterItem(TYPE_SECTION) {
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
            subtitleView.setTextOrHide(item.subtitle)
        }
    }
}

internal data class ChannelSubscriptionItem(
    override val id: String,
    val subscriptionId: String,
    val title: String?,
    val subtitle: String?
) : PrefCenterItem(TYPE_PREF_CHANNEL_SUBSCRIPTION) {
    companion object {
        @LayoutRes
        val LAYOUT: Int = R.layout.ua_item_preference
        @LayoutRes
        val WIDGET: Int = R.layout.ua_item_preference_widget_switch

        fun createViewHolder(
            parent: ViewGroup,
            inflater: LayoutInflater = LayoutInflater.from(parent.context),
        ): ViewHolder {
            val view = inflater.inflate(LAYOUT, parent, false)
            view.findViewById<LinearLayout>(R.id.ua_pref_widget)?.let { widgetRoot ->
                inflater.inflate(WIDGET, widgetRoot)
                widgetRoot.visibility = View.VISIBLE
            }
            return ViewHolder(view)
        }
    }

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

    class ViewHolder(itemView: View) : PrefCenterViewHolder<ChannelSubscriptionItem>(itemView) {

        private val switch: SwitchMaterial = itemView.findViewById(R.id.ua_pref_widget_switch)

        override fun bind(item: ChannelSubscriptionItem) {
            titleView.setTextOrHide(item.title)
            subtitleView.setTextOrHide(item.subtitle)

            switch.setOnCheckedChangeListener { _, isChecked ->
                Logger.verbose("ChannelSubscription Item checkedChange! ${item.subscriptionId} = $isChecked")
            }
        }
    }
}
