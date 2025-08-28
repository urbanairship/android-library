package com.urbanairship.preferencecenter.compose.ui.item

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.contacts.EmailRegistrationOptions
import com.urbanairship.contacts.SmsRegistrationOptions
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.compose.ui.ViewState
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactManagement.Platform
import com.urbanairship.preferencecenter.data.Item.ContactManagement.PromptDisplay

internal data class ContactManagementItem(
    val item: Item.ContactManagement,
) : BasePrefCenterItem(TYPE_CONTACT_MANAGEMENT) {

    sealed class Action() { data object Add : Action()
        data class Remove(val channel: ContactChannel) : Action()
        data class Resend(val channel: ContactChannel) : Action()
    }

    override val id: String = item.id
    override val conditions: Conditions = item.conditions

    val platform = item.platform
    val addPrompt = item.addPrompt
    val emptyLabel = item.emptyLabel
    val display = item.display

    override fun areItemsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (this === otherItem) return true
        if (javaClass != otherItem.javaClass) return false
        otherItem as ContactManagementItem
        return id == otherItem.id
    }

    override fun areContentsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (javaClass != otherItem.javaClass) return false
        otherItem as ContactManagementItem
        return item == otherItem.item
    }
}

@Composable
internal fun ContactManagementItem.toView(
    contactChannelsProvider: () -> Map<ContactChannel, ViewState.Content.ContactChannelState>,
    handler: (ContactManagementItem.Action) -> Unit
) {
    val channels = contactChannelsProvider
        .invoke()
        .filter { it.key.channelType == item.platform.channelType }

    Column(Modifier
        .fillMaxWidth()
        .padding(top = 4.dp, bottom = 8.dp)
        .padding(horizontal = 16.dp)
    ) {
        display.name?.let { text ->
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        display.description?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        if (channels.isEmpty()) {
            emptyView(item.emptyLabel)
        } else {
            listView(item,channels, handler)
        }

        OutlinedButton(
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, colorResource(R.color.ua_preference_center_divider_color)),
            onClick = { handler.invoke(ContactManagementItem.Action.Add) }
        ) {
            Text(
                text = item.addPrompt.button.text,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun listView(
    root: Item.ContactManagement,
    items: Map<ContactChannel, ViewState.Content.ContactChannelState>,
    handler: (ContactManagementItem.Action) -> Unit
) {
    val context = LocalContext.current

    Column(
        Modifier.fillMaxWidth()
    ) {
        items.entries
            .sortedBy { it.key.channelType }
            .forEach { (channel, state) ->
                Column(
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 64.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = when(root.platform) {
                            is Platform.Email -> Icons.Outlined.Email
                            is Platform.Sms -> Icons.Outlined.Phone
                        }

                        Icon(
                            modifier = Modifier
                                .size(24.dp, 24.dp)
                                .padding(end = 8.dp),
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )

                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                modifier = Modifier.padding(end = 4.dp),
                                text = channel.maskedAddress,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row {
                                if (state.showPendingButton) {
                                    Text(
                                        text = root.platform.resendOptions.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }

                                if (state.showResendButton) {
                                    Text(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .clickable(
                                                enabled = true,
                                                onClick = { handler.invoke(ContactManagementItem.Action.Resend(channel)) }
                                            )
                                            .semantics {
                                                contentDescription = root.platform.resendOptions.button.contentDescription
                                                    ?: root.platform.resendOptions.button.text
                                            } ,
                                        text = root.platform.resendOptions.button.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        IconButton(
                            modifier = Modifier
                                .size(width = 48.dp, height = 48.dp)
                                .semantics { contentDescription = deleteButtonContentDescription(
                                    context = context,
                                    item = root,
                                    address = channel.maskedAddress)
                                },
                            onClick = { handler.invoke(ContactManagementItem.Action.Remove(channel)) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
    }
}

private fun deleteButtonContentDescription(
    context: Context,
    item: Item.ContactManagement,
    address: String
): String {

    val platformDescription = when(item.platform) {
        is Platform.Email -> context.getString(R.string.ua_preference_center_contact_management_email_description)
        is Platform.Sms -> context.getString(R.string.ua_preference_center_contact_management_sms_description)
    }

    val addressDescription = address.replace(
        regex = """\*+""".toRegex(),
        replacement = context.getString(R.string.ua_preference_center_contact_management_redacted_description)
    )

    return "$platformDescription $addressDescription"
}

@Composable
private fun emptyView(text: String?) {
    Row(Modifier
        .fillMaxWidth()
        .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .size(24.dp, 24.dp)
                .padding(end = 4.dp)
                .semantics { hideFromAccessibility() }
            ,
            imageVector = Icons.Outlined.Info,
            tint = MaterialTheme.colorScheme.secondary,
            contentDescription = null
        )

        text?.let { item ->
            Text(
                modifier = Modifier.padding(end = 4.dp),
                text = item,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp
            )
        }
    }
}

@Preview
@Composable
private fun preview() {
    ContactManagementItem(
        item = Item.ContactManagement(
            id = "preview id",
            platform = Platform.Email(
                registrationOptions = Item.ContactManagement.RegistrationOptions.Email(
                    placeholder = "preview placeholder",
                    addressLabel = "preview addressLabel",
                    properties = null,
                    resendOptions = Item.ContactManagement.ResendOptions(
                        interval = 1,
                        message = "preview resend message",
                        button = Item.ContactManagement.LabeledButton(
                            text = "resend",
                            contentDescription = "resend content description"
                        ),
                        onSuccess = null
                    ),
                    errorMessages = Item.ContactManagement.ErrorMessages(
                        invalidMessage = "preview invalid message",
                        defaultMessage = "preview default message"
                    )
                )
            ),
            display = CommonDisplay(
                name = "preview name",
                description = "preview description",
            ),
            addPrompt = Item.ContactManagement.AddPrompt(
                prompt = Item.ContactManagement.AddChannelPrompt(
                    type = "email",
                    display = PromptDisplay(
                        title = "preview title",
                        description = "preview description",
                        footer = "preview footer",
                    ),
                    submitButton = Item.ContactManagement.LabeledButton(
                        text = "submit button",
                        contentDescription = "submit button content description",
                    ),
                    closeButton = Item.ContactManagement.IconButton(
                        contentDescription = "close button content description",
                    ),
                    cancelButton = Item.ContactManagement.LabeledButton(
                        text = "cancel button",
                        contentDescription = "cancel button content description",
                    ),
                    onSubmit = null,
                ),
                button = Item.ContactManagement.LabeledButton(
                    text = "add button",
                    contentDescription = "add button content description",
                )
            ),
            removePrompt = Item.ContactManagement.RemovePrompt(
                prompt = Item.ContactManagement.RemoveChannelPrompt(
                    type = "email",
                    display = PromptDisplay(
                        title = "preview title",
                        description = "preview description",
                        footer = "preview footer",
                    ),
                    submitButton = Item.ContactManagement.LabeledButton(
                        text = "submit button",
                        contentDescription = "submit button content description",
                    ),
                    closeButton = Item.ContactManagement.IconButton(
                        contentDescription = "close button content description",
                    ),
                    cancelButton = Item.ContactManagement.LabeledButton(
                        text = "cancel button",
                        contentDescription = "cancel button content description",
                    ),
                    onSubmit = null,
                ),
                button = Item.ContactManagement.IconButton(
                    contentDescription = "remove button content description",
                )
            ),
            emptyLabel = "empty label",
            conditions = emptyList()
        )
    ).toView(
        contactChannelsProvider = {
            mapOf(
                ContactChannel.Email(
                    registrationInfo = ContactChannel.Email.RegistrationInfo.Registered(
                        channelId = "preview channel id",
                        maskedAddress = "preview masked address",
                        transactionalOptedIn = 123,
                    )
                ) to ViewState.Content.ContactChannelState(showResendButton = true, showPendingButton = true),
                ContactChannel.Email(
                    registrationInfo = ContactChannel.Email.RegistrationInfo.Pending(
                        address = "pending address",
                        registrationOptions = EmailRegistrationOptions.commercialOptions()
                    )
                ) to ViewState.Content.ContactChannelState(showResendButton = false, showPendingButton = false),
                ContactChannel.Sms(
                    registrationInfo = ContactChannel.Sms.RegistrationInfo.Registered(
                        channelId = "preview channel id",
                        maskedAddress = "preview masked address",
                        isOptIn = true,
                        senderId = "preview id"
                    )
                ) to ViewState.Content.ContactChannelState(showResendButton = true, showPendingButton = false),
                ContactChannel.Sms(
                        registrationInfo = ContactChannel.Sms.RegistrationInfo.Pending(
                            address = "pending address",
                            registrationOptions = SmsRegistrationOptions.options("sender")
                        )
                ) to ViewState.Content.ContactChannelState(showResendButton = false, showPendingButton = true)
            )
        },
        handler = {}
    )
}
