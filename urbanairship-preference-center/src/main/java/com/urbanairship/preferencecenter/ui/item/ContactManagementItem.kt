package com.urbanairship.preferencecenter.ui.item

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.Provider
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactManagement.Platform
import com.urbanairship.preferencecenter.ui.CommonViewHolder
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State.Content.ContactChannelState
import com.urbanairship.preferencecenter.ui.isOptedIn
import com.urbanairship.preferencecenter.util.setTextOrHide
import com.urbanairship.util.AccessibilityUtils
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
            contactChannelsProvider: Provider<Map<ContactChannel, ContactChannelState>>,
            onAddClick: (position: Int) -> Unit,
            onRemoveClick: (position: Int, channel: ContactChannel) -> Unit,
            onResendClick: (position: Int, channel: ContactChannel) -> Unit,
        ): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(
                itemView = inflater.inflate(LAYOUT, parent, false),
                contactChannelsProvider = contactChannelsProvider,
                onAddClick = onAddClick,
                onRemoveClick = onRemoveClick,
                onResendClick = onResendClick,
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
        return item == otherItem.item
    }

    class ViewHolder(
        itemView: View,
        private val contactChannelsProvider: Provider<Map<ContactChannel, ContactChannelState>>,
        private val onAddClick: (Int) -> Unit,
        private val onRemoveClick: (position: Int, channel: ContactChannel) -> Unit,
        private val onResendClick: (position: Int, channel: ContactChannel) -> Unit,
    ) : CommonViewHolder<ContactManagementItem>(itemView) {
        private val context: Context
            get() = itemView.context

        private val addButton = itemView.findViewById<MaterialButton>(R.id.ua_pref_button)
        private val widget = itemView.findViewById<LinearLayout>(R.id.ua_pref_widget)

        override fun bind(item: ContactManagementItem) {
            titleView.setTextOrHide(item.display.name)
            descriptionView.setTextOrHide(item.display.description)

            val channels = contactChannelsProvider.get()
                .filter { it.key.channelType == item.platform.toChannelType() }

            widget.removeAllViews()
            if (channels.isEmpty()) {
                item.emptyLabel?.let {
                    val emptyView = makeEmptyView(context, it)
                    widget.addView(emptyView)
                }
            } else {
                val listView = makeListView(context, channels) { channel, state ->
                    makeListItem(itemView.context, item.item, channel, state)
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

        private fun makeListView(context: Context, items: Map<ContactChannel, ContactChannelState>, itemBuilder: (channel: ContactChannel, state: ContactChannelState) -> View): View {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.ua_item_contact_management_list, widget, false)

            val list = view.findViewById<LinearLayout>(R.id.ua_optin_list)

            for ((channel, state) in items) {
                list.addView(itemBuilder(channel, state))
            }

            return view
        }

        private fun makeListItem(
            context: Context,
            item: Item.ContactManagement,
            channel: ContactChannel,
            state: ContactChannelState,
        ): View {
            return LayoutInflater.from(context)
                .inflate(R.layout.ua_item_contact_management_list_item, widget, false)
                .apply {
                    // Clickable container for the whole item, not including the delete button
                    val info = findViewById<ViewGroup>(R.id.ua_optin_list_item_info)
                    val icon = findViewById<ImageView>(R.id.ua_optin_list_item_icon)
                    val text = findViewById<TextView>(R.id.ua_optin_list_item_text)
                    val delete = findViewById<ImageView>(R.id.ua_optin_list_item_delete)
                    val pending = findViewById<TextView>(R.id.ua_optin_list_item_pending)
                    val resend = findViewById<TextView>(R.id.ua_optin_list_item_resend)

                    // Set up common views
                    icon.setImageResource(itemIcon(item))
                    text.text = channel.maskedAddress
                    delete.run {
                        setOnClickListener { onRemoveClick(adapterPosition, channel) }
                        item.removePrompt.button.contentDescription?.let { contentDescription = it }
                    }

                    val resendOptions = item.registrationOptions.resendOptions

                    //  Set up pending view
                    pending.isVisible = if (state.showPendingButton) {
                        pending.text = resendOptions.message
                        true
                    } else {
                        false
                    }

                    //  Set up resend label
                    resend.isVisible = if (state.showResendButton) {
                        info.setOnClickListener { onResendClick(adapterPosition, channel) }
                        resend.text = resendOptions.button.text
                        true
                    } else {
                         false
                    }

                    // Set content description for info view
                    val (description, clickAction) =
                        itemContentDescription(item, channel.maskedAddress, channel.isOptedIn)
                    info.contentDescription = description
                    clickAction?.let { AccessibilityUtils.setClickActionLabel(info, it) }

                    // Set content description for delete button
                    val (deleteDescription, deleteClickAction) =
                        deleteContentDescription(item, channel.maskedAddress)
                    delete.contentDescription = deleteDescription
                    AccessibilityUtils.setClickActionLabel(delete, deleteClickAction)
                }
        }

        private fun itemContentDescription(
            item: Item.ContactManagement,
            maskedAddress: String,
            isOptedIn: Boolean
        ): Pair<String, String?> {
            val description = StringBuilder().apply {
                append(platformDescription(item.platform))
                append(" ")
                append(addressDescription(maskedAddress))
                statusDescription(isOptedIn)?.let {
                    append(" ")
                    append(it)
                }
            }

            val resendDescription = item.registrationOptions.resendOptions.button.contentDescription
                ?: item.registrationOptions.resendOptions.button.text

            val clickAction = if (!isOptedIn) { resendDescription } else null

            return description.toString() to clickAction
        }

        private fun deleteContentDescription(
            item: Item.ContactManagement,
            maskedAddress: String,
        ): Pair<String, String> {
            val description = StringBuilder().apply {
                append(platformDescription(item.platform))
                append(" ")
                append(addressDescription(maskedAddress))
            }

            val clickAction = item.removePrompt.button.contentDescription
                ?: context.getString(com.urbanairship.R.string.ua_delete)

            return description.toString() to clickAction
        }

        private fun statusDescription(isOptedIn: Boolean): String? {
            return if (!isOptedIn) {
                context.getString(R.string.ua_preference_center_contact_management_pending)
            } else {
                null
            }
        }

        private fun platformDescription(platform: Platform): String = when (platform) {
            Platform.EMAIL -> context.getString(R.string.ua_preference_center_contact_management_email_description)
            Platform.SMS -> context.getString(R.string.ua_preference_center_contact_management_sms_description)
        }

        private fun addressDescription(maskedAddress: String) =
            maskedAddress.replace(
                regex = contentDescriptionRedactedPattern,
                replacement = context.getString(R.string.ua_preference_center_contact_management_redacted_description)
            )

        @DrawableRes
        private fun itemIcon(item: Item.ContactManagement): Int {
            return if (item.platform == Platform.EMAIL) {
                R.drawable.ua_ic_preference_center_email
            } else {
                R.drawable.ua_ic_preference_center_phone
            }
        }

        private companion object {
            private val contentDescriptionRedactedPattern = """\*+""".toRegex()
        }
    }
}
