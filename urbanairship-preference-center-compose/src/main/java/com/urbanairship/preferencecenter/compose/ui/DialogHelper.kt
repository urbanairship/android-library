package com.urbanairship.preferencecenter.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.R
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactManagement.RemoveChannelPrompt

@OptIn(ExperimentalMaterial3Api::class)
internal data object ContactDialogHelper {

    @Composable
    fun actionableMessageDialog(
        message: Item.ContactManagement.ActionableMessage,
        onDismiss: () -> Unit,
    ) {
        BasicAlertDialog(
            onDismissRequest = onDismiss
        ) {
            MaterialTheme {
                Surface(
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(Modifier
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            modifier = Modifier.padding(bottom = 12.dp),
                            text = message.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        message.description?.let { text ->
                            Text(
                                modifier = Modifier.padding(bottom = 16.dp),
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onDismiss) {
                                Text(message.button.text)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun removeChannel(
        prompt: Item.ContactManagement.RemovePrompt,
        onPositiveAction: () -> Unit,
        onNegativeOrDismiss: () -> Unit
    ) {
        BasicAlertDialog(
            onDismissRequest = onNegativeOrDismiss
        ) {
            MaterialTheme {
                Surface(
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(Modifier
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            modifier = Modifier.padding(bottom = 12.dp),
                            text = prompt.prompt.display.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        prompt.prompt.display.description?.let { text ->
                            Text(
                                modifier = Modifier.padding(bottom = 16.dp),
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Row(Modifier.padding(bottom = 12.dp)) {
                            TextButton(onNegativeOrDismiss) {
                                Text(prompt.prompt.cancelButton?.text ?: stringResource(R.string.ua_cancel))
                            }

                            Spacer(Modifier.weight(1f))

                            TextButton(onPositiveAction) {
                                Text(
                                    text = prompt.prompt.submitButton.text,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview("Add Confirmation")
@Composable
private fun previewAddConfirm() {
    ContactDialogHelper.actionableMessageDialog(
        message = Item.ContactManagement.ActionableMessage(
            title = "Add Confirmation Title",
            description = "Add Confirmation Description",
            button = Item.ContactManagement.LabeledButton(
                text = "Add Confirmation Button",
                contentDescription = null
            ),
            contentDescription = null
        ),
        onDismiss = { }
    )
}

@Preview("Remove Channel")
@Composable
private fun previewRemove() {
    ContactDialogHelper.removeChannel(
        prompt = Item.ContactManagement.RemovePrompt(
            prompt = RemoveChannelPrompt(
                type = "preview",
                display = Item.ContactManagement.PromptDisplay(
                    title = "Remove Channel Preview",
                    description = "Remove Channel Preview Subtitle",
                    footer = null
                ),
                submitButton = Item.ContactManagement.LabeledButton(
                    text = "On Submit",
                    contentDescription = null
                ),
                closeButton = Item.ContactManagement.IconButton(
                    contentDescription = null
                ),
                cancelButton = Item.ContactManagement.LabeledButton(
                    text = "Cancel",
                    contentDescription = null
                ),
                onSubmit = null
            ),
            button = Item.ContactManagement.IconButton(
                contentDescription = null
            )
        ),
        onPositiveAction = {},
        onNegativeOrDismiss = {}
    )
}
