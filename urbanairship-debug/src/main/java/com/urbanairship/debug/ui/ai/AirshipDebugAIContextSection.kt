/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.SwipeToDeleteRow

/**
 * Reusable "Context" section: pick where context comes from and, for the custom modes, edit the
 * items inline. Also renders the resolved context the next run will actually use, so the
 * override/append behavior is visible.
 */
@Composable
internal fun AirshipDebugAIContextSection(
    mode: AirshipDebugContextMode,
    onModeChange: (AirshipDebugContextMode) -> Unit,
    items: List<AirshipDebugContextItem>,
    onItemsChange: (List<AirshipDebugContextItem>) -> Unit,
    resolvedItems: List<EvaluationContext.Item>?,
    isFetching: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Section(modifier = modifier, title = "Context") {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AirshipDebugContextMode.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = mode == entry,
                        onClick = { onModeChange(entry) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AirshipDebugContextMode.entries.size
                        )
                    ) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (mode != AirshipDebugContextMode.ProviderOnly) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    items.forEachIndexed { index, item ->
                        SwipeToDeleteRow(
                            content = {
                                AirshipDebugContextItemRow(
                                    item = item,
                                    onChange = { updated ->
                                        onItemsChange(items.toMutableList().also { it[index] = updated })
                                    }
                                )
                            },
                            onDelete = { onItemsChange(items.filterNot { it.id == item.id }) }
                        )
                        HorizontalDivider()
                    }

                    TextButton(onClick = { onItemsChange(items + AirshipDebugContextItem()) }) {
                        Text("+ Add context item")
                    }
                }
            }

            ResolvedContextPreview(isFetching = isFetching, resolvedItems = resolvedItems)

            Text(
                text = footerText(mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun ResolvedContextPreview(
    isFetching: Boolean,
    resolvedItems: List<EvaluationContext.Item>?
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        when {
            isFetching -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp
                )
                Text("Fetching context…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            !resolvedItems.isNullOrEmpty() -> {
                Text(
                    text = "Resolved context sent to the model",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                resolvedItems.forEach { item ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "priority ${item.priority}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = item.content, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            else -> Text("No context resolved", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun footerText(mode: AirshipDebugContextMode): String = when (mode) {
    AirshipDebugContextMode.ProviderOnly ->
        "Uses the app's registered context provider, exactly as production would."
    AirshipDebugContextMode.Append ->
        "Provider context plus the items above (added after, so they win priority ties when trimmed)."
    AirshipDebugContextMode.Override ->
        "Ignores the provider entirely and sends only the items above. Restored when you leave this screen."
}

/** One editable context item row: free-text content plus a priority stepper. */
@Composable
internal fun AirshipDebugContextItemRow(
    item: AirshipDebugContextItem,
    onChange: (AirshipDebugContextItem) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        TextField(
            value = item.content,
            onValueChange = { onChange(item.copy(content = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Context item, e.g. User interests: dogs") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            minLines = 1
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "priority",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { onChange(item.copy(priority = item.priority - 1)) }) { Text("−") }
            Text(text = item.priority.toInt().toString())
            TextButton(onClick = { onChange(item.copy(priority = item.priority + 1)) }) { Text("+") }
        }
    }
}

/**
 * Shows the assembled system instructions and rendered user prompt for a usage, so the exact
 * text sent to the model is visible before running.
 */
@Composable
internal fun AirshipDebugAIPromptPreview(
    instructions: String,
    prompt: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AirshipDebugCodeBlock(label = "Instructions (system)", text = instructions)
        Column(modifier = Modifier.padding(top = 8.dp)) {
            AirshipDebugCodeBlock(label = "Prompt (user)", text = prompt)
        }
    }
}

/** A full-width, bold button used to trigger a sandbox's evaluation/inference run. */
@Composable
internal fun EvaluateButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label)
    }
}

/** A labeled, monospaced, copy-selectable block of text — used for prompts and raw JSON output. */
@Composable
internal fun AirshipDebugCodeBlock(label: String, text: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = text.ifEmpty { "—" },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    )
                    .padding(8.dp)
            )
        }
    }
}
