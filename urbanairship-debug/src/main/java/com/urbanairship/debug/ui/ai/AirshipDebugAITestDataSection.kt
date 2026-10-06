/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.SwipeToDeleteRow
import java.text.DateFormat
import java.util.Date

/**
 * Reusable "Test Data" section shared by the AI debug sandboxes.
 *
 * Lets you drop in a built-in example or a saved preset, save the current inputs as a new
 * preset, and import/export inputs as JSON to share a case with someone else. Autosave of the
 * working session is handled by the owning view model — this is only the explicit, named
 * test-data management.
 */
@Composable
internal fun <State : Any> AirshipDebugAITestDataSection(
    store: AirshipDebugAIStore<State>,
    currentState: () -> State,
    onLoad: (State) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var showingExamples by remember { mutableStateOf(false) }
    var showingSave by remember { mutableStateOf(false) }
    var showingExport by remember { mutableStateOf(false) }
    var showingImport by remember { mutableStateOf(false) }

    Section(modifier = modifier, title = "Test Data") {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Box {
                TextButton(onClick = { showingExamples = true }) { Text("Load example") }
                DropdownMenu(
                    expanded = showingExamples,
                    onDismissRequest = { showingExamples = false }
                ) {
                    if (store.bundledFixtures.isEmpty()) {
                        DropdownMenuItem(text = { Text("No examples") }, onClick = {}, enabled = false)
                    } else {
                        store.bundledFixtures.forEach { fixture ->
                            DropdownMenuItem(
                                text = { Text(fixture.name) },
                                onClick = {
                                    onLoad(fixture.state)
                                    showingExamples = false
                                }
                            )
                        }
                    }
                }
            }

            store.presets.forEach { preset ->
                SwipeToDeleteRow(
                    content = {
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoad(preset.state) },
                            headlineContent = { Text(preset.name) }
                        )
                    },
                    onDelete = { store.deletePreset(preset.id) }
                )
            }

            TextButton(onClick = { showingSave = true }) { Text("Save current as preset…") }
            TextButton(onClick = { showingExport = true }) { Text("Export JSON") }
            TextButton(onClick = { showingImport = true }) { Text("Import JSON…") }

            Text(
                text = "Inputs auto-save and restore next time. Save named presets or import/export JSON to share cases.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }

    if (showingSave) {
        SavePresetDialog(
            onSave = { name ->
                store.savePreset(name = name, state = currentState())
                showingSave = false
            },
            onDismiss = { showingSave = false }
        )
    }

    if (showingExport) {
        ExportJsonDialog(
            json = store.exportJSON(currentState()),
            onDismiss = { showingExport = false }
        )
    }

    if (showingImport) {
        ImportJsonDialog(
            store = store,
            onLoad = {
                onLoad(it)
                showingImport = false
            },
            onDismiss = { showingImport = false }
        )
    }
}

@Composable
private fun SavePresetDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Preset") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ExportJsonDialog(json: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export JSON") },
        text = {
            SelectionContainer {
                Text(
                    text = json,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(json)) }) { Text("Copy") }
        }
    )
}

@Composable
private fun <State : Any> ImportJsonDialog(
    store: AirshipDebugAIStore<State>,
    onLoad: (State) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { text.takeIf { it.isNotBlank() }?.let(store::importJSON) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import JSON") },
        text = {
            Column {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Paste exported JSON") },
                    minLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isNotBlank() && parsed == null) {
                    Text(
                        text = "Not valid JSON for this screen.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onLoad) },
                enabled = parsed != null
            ) { Text("Load") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Reusable "Run History" section: the last handful of runs for a usage, tap-to-restore. */
@Composable
internal fun <State : Any> AirshipDebugAIHistorySection(
    store: AirshipDebugAIStore<State>,
    onRestore: (State) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Section(modifier = modifier, title = "Run History") {
        Column {
            if (store.history.isEmpty()) {
                Text(
                    text = "No runs yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                store.history.forEach { record ->
                    ListItem(
                        modifier = Modifier.clickable { onRestore(record.state) },
                        headlineContent = {
                            Text(record.summary.ifEmpty { "(run)" }, maxLines = 1)
                        },
                        supportingContent = {
                            Text(record.output, maxLines = 2, fontFamily = FontFamily.Monospace)
                        },
                        trailingContent = {
                            Text(
                                text = timeFormatter.format(Date(record.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    HorizontalDivider()
                }

                TextButton(onClick = { store.clearHistory() }) { Text("Clear history") }
            }

            Text(
                text = "Tap a run to restore its inputs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

private val timeFormatter: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
