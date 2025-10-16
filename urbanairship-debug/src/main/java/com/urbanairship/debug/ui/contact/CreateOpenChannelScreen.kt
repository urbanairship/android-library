/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.contacts.OpenChannelRegistrationOptions
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun CreateOpenChannelScreen(
    viewModel: CreateOpenChannelViewModel = viewModel(),
    onNavigateUp: () -> Unit = {},
) {

    val showAddDialog = remember { mutableStateOf(false) }

    DebugScreen(
        title = stringResource(id = ContactScreens.AddChannel.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(
            viewModel = viewModel,
            onNavigateUp = onNavigateUp,
            onShowInputDialog = { showAddDialog.value = true }
        )

        if (showAddDialog.value) {
            AddIdentifierDialog { entry ->
                entry?.let { viewModel.addIdentifier(it.first, it.second) }
                showAddDialog.value = false
            }
        }
    }
}

@Composable
private fun ScreenContent(
    viewModel: CreateOpenChannelViewModel,
    onNavigateUp: () -> Unit,
    onShowInputDialog: () -> Unit
) {
    LazyColumn {
        item {
            Section(title = "Channel Info") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)) {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        value = viewModel.platform.value,
                        onValueChange = { viewModel.platform.value = it.trim()},
                        label = { Text("Platform") },
                        keyboardOptions = KeyboardOptions(autoCorrect = false)
                    )

                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        value = viewModel.address.value,
                        onValueChange = { viewModel.address.value = it.trim()},
                        label = { Text("Address") },
                        keyboardOptions = KeyboardOptions(autoCorrect = false)
                    )
                }
            }
        }

        item {
            Spacer(Modifier.padding(top = 16.dp))

            val identifiers = viewModel.identifiers.value
            Section(title = "Identifiers") {

                RowItem(
                    modifier = Modifier.clickable { onShowInputDialog.invoke() },
                    title = "Add Identifier",
                    accessory = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "display"
                        )
                    }
                )

                identifiers.forEach { entry ->
                    RowItem(
                        title = "${entry.key}:${entry.value}",
                        accessory = {
                            IconButton(onClick = { viewModel.removeIdentifier(entry.key) }) {
                                Icon(Icons.Default.Delete, contentDescription = "delete")
                            }
                        }
                    )
                }
            }
        }

        item {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                enabled = viewModel.isComplete,
                onClick = {
                    viewModel.perform()
                    onNavigateUp()
                }
            ) {
                Text(text = "Apply")
            }
        }
    }
}

@Composable
private fun AddIdentifierDialog(onDismiss: (Pair<String, String>?) -> Unit) {
    val key = remember { mutableStateOf("") }
    val value = remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = "add-identifier") {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { onDismiss(null) },
        confirmButton = {
            TextButton(onClick = { onDismiss(Pair(key.value, value.value)) }) {
                Text("Add")
            }
        },
        title = { Text(text = "Add New Identifier") },
        text = {
            Column {
                TextField(
                    modifier = Modifier.focusRequester(focusRequester),
                    value = key.value,
                    onValueChange = { key.value = it.trim() },
                    label = { Text("Key") },
                    keyboardOptions = KeyboardOptions(autoCorrect = false),
                )
                TextField(
                    value = value.value,
                    onValueChange = { value.value = it.trim() },
                    label = { Text("Value") },
                    keyboardOptions = KeyboardOptions(autoCorrect = false),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(null) }) {
                Text("Cancel")
            }
        }
    )
}

internal class CreateOpenChannelViewModel: ViewModel() {
    val platform = mutableStateOf("")
    val address = mutableStateOf("")
    val identifiers = mutableStateOf<Map<String, String>>(emptyMap())

    val isComplete: Boolean
        get() = platform.value.isNotEmpty() && address.value.isNotEmpty()

    fun perform() {
        if (!Airship.isFlying || platform.value.isEmpty() || address.value.isEmpty()) {
            return
        }

        val options = OpenChannelRegistrationOptions.options(platform.value, identifiers.value)
        Airship.contact.registerOpenChannel(address.value, options)
    }

    fun addIdentifier(key: String, value: String) {
        if (key.isEmpty() || value.isEmpty()) {
            return
        }

        val updated = identifiers.value.toMutableMap().apply {
            put(key, value)
        }

        identifiers.value = updated
    }

    fun removeIdentifier(key: String) {
        val updated = identifiers.value.toMutableMap().apply { remove(key) }
        identifiers.value = updated
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        CreateOpenChannelScreen()
    }
}
