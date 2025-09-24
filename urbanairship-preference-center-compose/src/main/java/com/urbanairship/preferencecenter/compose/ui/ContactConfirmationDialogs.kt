package com.urbanairship.preferencecenter.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.R
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactManagement.RemoveChannelPrompt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactActionableMessageDialog(
    message: Item.ContactManagement.ActionableMessage,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = PrefCenterTheme.shapes.contactManagementDialog,
            color = PrefCenterTheme.colors.contactManagementDialogBackground,
        ) {
            Column(Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogPadding)) {
                Text(
                    text = message.title,
                    style = PrefCenterTheme.typography.contactManagementDialogTitle,
                    color = PrefCenterTheme.colors.contactManagementDialogTitleText,
                    modifier = Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogTitlePadding)
                )

                message.description?.let { text ->
                    Text(
                        text = text,
                        style = PrefCenterTheme.typography.contactManagementDialogDescription,
                        color = PrefCenterTheme.colors.contactManagementDialogDescriptionText,
                        modifier = Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogDescriptionPadding)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onDismiss) {
                        Text(
                            text = message.button.text,
                            color = PrefCenterTheme.colors.contactManagementDialogButtonLabelPositive,
                            style = PrefCenterTheme.typography.contactManagementButtonLabel
                        )
                    }
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactRemoveDialog(
    prompt: Item.ContactManagement.RemovePrompt,
    onPositiveAction: () -> Unit,
    onNegativeOrDismiss: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onNegativeOrDismiss
    ) {
        Surface(
            shape = PrefCenterTheme.shapes.contactManagementDialog,
            color = PrefCenterTheme.colors.contactManagementDialogBackground,
        ) {
            Column(Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogPadding)) {
                Text(
                    text = prompt.prompt.display.title,
                    style = PrefCenterTheme.typography.contactManagementDialogTitle,
                    color = PrefCenterTheme.colors.contactManagementDialogTitleText,
                    modifier = Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogTitlePadding)
                )

                prompt.prompt.display.description?.let { text ->
                    Text(
                        text = text,
                        style = PrefCenterTheme.typography.contactManagementDialogDescription,
                        color = PrefCenterTheme.colors.contactManagementDialogDescriptionText,
                        modifier = Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogDescriptionPadding)
                    )
                }

                Row {
                    TextButton(onClick = onNegativeOrDismiss) {
                        Text(
                            text = prompt.prompt.cancelButton?.text ?: stringResource(R.string.ua_cancel),
                            color = PrefCenterTheme.colors.contactManagementDialogButtonLabelNeutral,
                            style = PrefCenterTheme.typography.contactManagementButtonLabel
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onPositiveAction) {
                        Text(
                            text = prompt.prompt.submitButton.text,
                            color = PrefCenterTheme.colors.contactManagementDialogButtonLabelPositive,
                            style = PrefCenterTheme.typography.contactManagementButtonLabel
                        )
                    }
                }
            }
        }
    }
}

@Preview("Add Confirmation")
@Composable
private fun previewAddConfirm() {
    PreferenceCenterTheme {
        ContactActionableMessageDialog(
            message = Item.ContactManagement.ActionableMessage(
                title = "Add Contact Info",
                description = "Are you sure you want to add this channel to your contact information?",
                button = Item.ContactManagement.LabeledButton(
                    text = "Add", contentDescription = null
                ),
                contentDescription = null
            ),
            onDismiss = {}
        )
    }
}

@Preview("Remove Channel")
@Composable
private fun previewRemove() {
    PreferenceCenterTheme {
        ContactRemoveDialog(
            prompt = Item.ContactManagement.RemovePrompt(
                prompt = RemoveChannelPrompt(
                    type = "preview",
                    display = Item.ContactManagement.PromptDisplay(
                        title = "Remove Contact Info",
                        description = "Are you sure you want to remove this channel from your contact information?",
                        footer = null
                    ),
                    submitButton = Item.ContactManagement.LabeledButton("Submit", contentDescription = null),
                    closeButton = Item.ContactManagement.IconButton(contentDescription = null),
                    cancelButton = Item.ContactManagement.LabeledButton("Cancel", contentDescription = null),
                    onSubmit = null
                ),
                button = Item.ContactManagement.IconButton(contentDescription = null)
            ),
            onPositiveAction = {},
            onNegativeOrDismiss = {}
        )
    }
}
