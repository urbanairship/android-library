package com.urbanairship.preferencecenter.ui.item

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.Provider
import com.urbanairship.UALog
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactManagement.Platform.EMAIL
import com.urbanairship.preferencecenter.ui.CommonViewHolder
import com.urbanairship.preferencecenter.util.setTextOrHide
import com.google.android.material.button.MaterialButton

internal typealias ContactManagementChannelsProvider = () -> List<String>

internal data class ContactManagementItem(
    val item: Item.ContactManagement,
) :
    PrefCenterItem(TYPE_CONTACT_MANAGEMENT) {
    companion object {
        @LayoutRes
        val LAYOUT: Int = R.layout.ua_item_contact_management

        fun createViewHolder(
            parent: ViewGroup,
            contactChannelsProvider: Provider<Set<ContactChannel>>,
            onAddClick: (position: Int) -> Unit,
            onRemoveClick: (position: Int, channel: ContactChannel) -> Unit,
        ): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(
                itemView = inflater.inflate(LAYOUT, parent, false),
                contactChannelsProvider = contactChannelsProvider,
                onAddClick = onAddClick,
                onRemoveClick = onRemoveClick,
            )
        }
    }

    override val id: String = item.id
    override val conditions: Conditions = item.conditions

    val platform = item.platform
    val addPrompt = item.addPrompt
    val emptyLabel = item.emptyLabel
    val display = item.display

    override fun areItemsTheSame(otherItem: PrefCenterItem): Boolean {
        if (this === otherItem) return true
        if (javaClass != otherItem.javaClass) return false
        otherItem as ContactManagementItem
        return id == otherItem.id
    }

    override fun areContentsTheSame(otherItem: PrefCenterItem): Boolean {
        if (javaClass != otherItem.javaClass) return false
        otherItem as ContactManagementItem
        return item == otherItem.item // TODO: Check if this is correct
    }

    class ViewHolder(
        itemView: View,
        private val contactChannelsProvider: Provider<Set<ContactChannel>>,
        private val onAddClick: (Int) -> Unit,
        private val onRemoveClick: (position: Int, channel: ContactChannel) -> Unit,
    ) : CommonViewHolder<ContactManagementItem>(itemView) {

        private val addButton = itemView.findViewById<MaterialButton>(R.id.ua_pref_button)
        private val widget = itemView.findViewById<LinearLayout>(R.id.ua_pref_widget)

        override fun bind(item: ContactManagementItem) {
            titleView.setTextOrHide(item.display.name)
            descriptionView.setTextOrHide(item.display.description)

            val channels = contactChannelsProvider.get()
                .filter { it.channelType == item.platform.toChannelType() }

            UALog.e { "ContactManagementItem BIND!!! channels: $channels" }

            widget.removeAllViews()
            if (channels.isEmpty()) {
                val emptyView = makeEmptyView(itemView.context, item.emptyLabel ?: "No XXXX added") //TODO: what do if empty label is null? default from strings?
                widget.addView(emptyView)
            } else {
                val listView = makeListView(itemView.context, channels) { channel ->
                    makeListItem(itemView.context, channel, item.platform)
                }
                widget.addView(listView)
            }

            val btn = item.addPrompt.button
            with (addButton) {
                text = btn.text
                btn.contentDescription?.let { contentDescription = it }

                setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onAddClick(adapterPosition)
                    }
                }
            }
        }

        private fun makeEmptyView(context: Context, emptyLabel: String): View {
            val emptyView = LayoutInflater.from(context).inflate(R.layout.ua_item_contact_management_empty, widget, false)
            emptyView.findViewById<TextView>(R.id.ua_optin_empty_label).text = emptyLabel
            return emptyView
        }

        private fun makeListView(context: Context, items: List<ContactChannel>, itemBuilder: (item: ContactChannel) -> View): View {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.ua_item_contact_management_list, widget, false)

            val list = view.findViewById<LinearLayout>(R.id.ua_optin_list)

            for (item in items) {
                list.addView(itemBuilder(item))
            }

            return view
        }

        private fun makeListItem(
            context: Context,
            channel: ContactChannel,
            platform: Item.ContactManagement.Platform,
        ): View {
            return LayoutInflater.from(context)
                .inflate(R.layout.ua_item_contact_management_list_item, widget, false)
                .apply {
                    val icon = findViewById<ImageView>(R.id.ua_optin_list_item_icon)
                    val text = findViewById<TextView>(R.id.ua_optin_list_item_text)
                    val delete = findViewById<ImageView>(R.id.ua_optin_list_item_delete)
                    val pending = findViewById<TextView>(R.id.ua_optin_list_item_pending)

                    icon.setImageResource(
                        if (platform == EMAIL) {
                            R.drawable.ua_ic_preference_center_email
                        } else {
                            R.drawable.ua_ic_preference_center_phone
                        }
                    )

                    pending.isVisible = !channel.isRegistered

                    text.text = channel.maskedAddress

                    delete.setOnClickListener {
                        onRemoveClick(adapterPosition, channel)
                    }
                }
        }
    }
}
