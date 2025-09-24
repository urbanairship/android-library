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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.contacts.EmailRegistrationOptions
import com.urbanairship.contacts.SmsRegistrationOptions
import com.urbanairship.preferencecenter.compose.ui.ViewState
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.core.R
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

    override val conditions: Conditions = item.conditions

    val platform = item.platform
    val addPrompt = item.addPrompt
    val emptyLabel = item.emptyLabel
    val display = item.display
}

@Composable
internal fun ContactManagementItem.Content(
    contactChannelsProvider: () -> Map<ContactChannel, ViewState.Content.ContactChannelState>,
    handler: (ContactManagementItem.Action) -> Unit
) {
    val channels = contactChannelsProvider.invoke().filter {
        it.key.channelType == item.platform.channelType
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(PrefCenterTheme.dimens.itemPadding)
    ) {
        display.name?.let { text ->
            Text(
                text = text,
                style = PrefCenterTheme.typography.itemTitle,
                modifier = Modifier.padding(PrefCenterTheme.dimens.itemTitlePadding),
                color = PrefCenterTheme.colors.contactManagementItemTitleText
            )
        }

        display.description?.let { text ->
            Text(
                text = text,
                style = PrefCenterTheme.typography.itemDescription,
                modifier = Modifier.padding(PrefCenterTheme.dimens.itemDescriptionPadding),
                color = PrefCenterTheme.colors.contactManagementItemDescriptionText
            )
        }

        if (channels.isEmpty()) {
            emptyView(item.emptyLabel)
        } else {
            listView(item, channels, handler)
        }

        OutlinedButton(
            shape = PrefCenterTheme.shapes.contactManagementAddButton,
            border = BorderStroke(1.dp, PrefCenterTheme.colors.divider),
            onClick = { handler.invoke(ContactManagementItem.Action.Add) }
        ) {
            Text(
                text = item.addPrompt.button.text,
                style = PrefCenterTheme.typography.contactManagementButtonLabel,
                color = PrefCenterTheme.colors.contactManagementAddButtonText
            )
        }
    }
}

@Composable
private fun listView(
    item: Item.ContactManagement,
    items: Map<ContactChannel, ViewState.Content.ContactChannelState>,
    handler: (ContactManagementItem.Action) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        items.entries.sortedBy { it.key.channelType }.forEach { (channel, state) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val clickModifier = if (state.showResendButton) {
                    Modifier.clickable {
                        handler.invoke(ContactManagementItem.Action.Resend(channel))
                    }
                } else {
                    Modifier
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                        .then(clickModifier)
                        .semantics(mergeDescendants = true) {
                            // TODO: handle a11y description for merged children?
                            // .semantics {
                            //     contentDescription =
                            //         item.platform.resendOptions.button.contentDescription
                            //             ?: item.platform.resendOptions.button.text
                            // },
                        }
                ) {
                    Icon(
                        imageVector = when(item.platform) {
                            is Platform.Email -> Icons.Outlined.Email
                            is Platform.Sms -> Icons.Outlined.Phone
                        },
                        tint = PrefCenterTheme.colors.contactManagementItemIconTint,
                        contentDescription = null,
                        modifier = Modifier.size(PrefCenterTheme.dimens.contactManagementItemIconSize)
                    )

                    Spacer(Modifier.width(PrefCenterTheme.dimens.contactManagementItemIconSpacing))

                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .sizeIn(minHeight = PrefCenterTheme.dimens.contactManagementItemMinHeight)
                    ) {
                        Text(
                            text = channel.maskedAddress,
                            style = PrefCenterTheme.typography.contactManagementItemDescription,
                            color = PrefCenterTheme.colors.contactManagementItemAddressText,
                            modifier = Modifier.padding(PrefCenterTheme.dimens.contactManagementItemTitlePadding)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.showPendingButton) {
                                Text(
                                    text = item.platform.resendOptions.message,
                                    style = PrefCenterTheme.typography.contactManagementItemDescription,
                                    color = PrefCenterTheme.colors.contactManagementItemStatusText
                                )
                            }

                            if (state.showResendButton) {
                                Spacer(Modifier.width(PrefCenterTheme.dimens.contactManagementItemActionSpacing))

                                Text(
                                    text = item.platform.resendOptions.button.text,
                                    style = PrefCenterTheme.typography.contactManagementItemDescription,
                                    color = PrefCenterTheme.colors.contactManagementItemActionText
                                )
                            }
                        }
                    }
                }

                IconButton(
                    modifier = Modifier.size(PrefCenterTheme.dimens.contactManagementItemDeleteIconSize),
                    onClick = { handler.invoke(ContactManagementItem.Action.Remove(channel)) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = item.deleteButtonContentDescription(context, channel.maskedAddress),
                        tint = PrefCenterTheme.colors.contactManagementItemDeleteIconTint
                    )
                }
            }
        }
    }
}

@Composable
private fun emptyView(text: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .sizeIn(minHeight = PrefCenterTheme.dimens.contactManagementItemMinHeight)
    ) {
        text?.let { item ->
            Icon(
                modifier = Modifier
                    .size(PrefCenterTheme.dimens.contactManagementItemIconSize),
                imageVector = Icons.Outlined.Info,
                tint = PrefCenterTheme.colors.contactManagementItemIconTint,
                contentDescription = null
            )

            Spacer(Modifier.width(PrefCenterTheme.dimens.contactManagementItemIconSpacing))

            Text(
                text = item,
                style = PrefCenterTheme.typography.contactManagementItemDescription,
                color = PrefCenterTheme.colors.contactManagementItemDescriptionText,
            )
        }
    }
}

private fun Item.ContactManagement.deleteButtonContentDescription(
    context: Context,
    address: String
): String {

    val platformDescription = when(platform) {
        is Platform.Email -> context.getString(R.string.ua_preference_center_contact_management_email_description)
        is Platform.Sms -> context.getString(R.string.ua_preference_center_contact_management_sms_description)
    }

    val addressDescription = address.replace(
        regex = """\*+""".toRegex(),
        replacement = context.getString(R.string.ua_preference_center_contact_management_redacted_description)
    )

    return "$platformDescription $addressDescription"
}

@Preview
@Composable
private fun previewEmpty() {
    PreferenceCenterTheme {
        Surface {
            previewItem.Content(
                contactChannelsProvider = { emptyMap() },
                handler = {}
            )
        }
    }
}

@Preview
@Composable
private fun preview() {
    PreferenceCenterTheme {
        Surface {
            previewItem.Content(contactChannelsProvider = {
                mapOf(
                    ContactChannel.Email(
                        registrationInfo = ContactChannel.Email.RegistrationInfo.Registered(
                            channelId = "preview channel id",
                            maskedAddress = "e******@example.com",
                            transactionalOptedIn = 123,
                        )
                    ) to ViewState.Content.ContactChannelState(
                        showResendButton = false, showPendingButton = false
                    ), ContactChannel.Email(
                        registrationInfo = ContactChannel.Email.RegistrationInfo.Pending(
                            address = "t***@example.com",
                            registrationOptions = EmailRegistrationOptions.commercialOptions()
                        )
                    ) to ViewState.Content.ContactChannelState(
                        showResendButton = true, showPendingButton = true
                    ), ContactChannel.Sms(
                        registrationInfo = ContactChannel.Sms.RegistrationInfo.Registered(
                            channelId = "preview channel id",
                            maskedAddress = "preview masked address",
                            isOptIn = true,
                            senderId = "preview id"
                        )
                    ) to ViewState.Content.ContactChannelState(
                        showResendButton = true, showPendingButton = false
                    ), ContactChannel.Sms(
                        registrationInfo = ContactChannel.Sms.RegistrationInfo.Pending(
                            address = "pending address",
                            registrationOptions = SmsRegistrationOptions.options("sender")
                        )
                    ) to ViewState.Content.ContactChannelState(
                        showResendButton = false, showPendingButton = true
                    )
                )
            }, handler = {})
        }
    }
}

private val previewItem = ContactManagementItem(
    item = Item.ContactManagement(
        id = "preview id", platform = Platform.Email(
            registrationOptions = Item.ContactManagement.RegistrationOptions.Email(
                placeholder = "preview placeholder",
                addressLabel = "preview addressLabel",
                properties = null,
                resendOptions = Item.ContactManagement.ResendOptions(
                    interval = 1,
                    message = "Pending verification",
                    button = Item.ContactManagement.LabeledButton(
                        text = "Resend",
                        contentDescription = "resend content description"
                    ),
                    onSuccess = null
                ),
                errorMessages = Item.ContactManagement.ErrorMessages(
                    invalidMessage = "preview invalid message",
                    defaultMessage = "preview default message"
                )
            )
        ), display = CommonDisplay(
            name = "Email Addresses",
            description = "Register your email address to receive updates.",
        ), addPrompt = Item.ContactManagement.AddPrompt(
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
            ), button = Item.ContactManagement.LabeledButton(
                text = "Add email address",
                contentDescription = "add button content description",
            )
        ), removePrompt = Item.ContactManagement.RemovePrompt(
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
            ), button = Item.ContactManagement.IconButton(
                contentDescription = "remove button content description",
            )
        ), emptyLabel = "There are no email addresses opted-in.", conditions = emptyList()
    )
)
