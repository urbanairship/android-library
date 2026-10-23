package com.urbanairship.devapp.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.urbanairship.devapp.messagecenter.DevappMessageCenterTheme
import com.urbanairship.devapp.ui.theme.tvButtonColors
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.ui.MessageCenterListState
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessage
import com.urbanairship.messagecenter.compose.ui.MessageCenterState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())

@Composable
internal fun TvMessageCenterScreen(state: MessageCenterState) {
    val listState = state.listState
    val selectedId = state.messageState.messageId

    val listFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }

    var previousSelectedId by remember { mutableStateOf(selectedId) }
    LaunchedEffect(selectedId) {
        when {
            selectedId != null -> detailFocus.requestFocus()
            previousSelectedId != null -> listFocus.requestFocus()
        }
        previousSelectedId = selectedId
    }

    BackHandler(enabled = selectedId != null) {
        state.clearMessage()
    }

    DevappMessageCenterTheme {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 27.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ListActions(listState)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(listFocus)
                        .focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        listState.isLoading -> item { Text("Loading…") }
                        listState.isError -> item {
                            Button(colors = tvButtonColors(), onClick ={ listState.refresh() }) { Text("Failed to load messages. Retry") }
                        }
                        listState.messages.isEmpty() -> item { Text("No messages") }
                        else -> items(listState.messages, key = { it.id }) { message ->
                            MessageRow(
                                message = message,
                                listState = listState,
                                onSelect = {
                                    state.selectMessage(message.id)
                                    listState.setHighlighted(message.id)
                                }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedId == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a message")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.messageState.title.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            colors = tvButtonColors(),
                            modifier = Modifier.focusRequester(detailFocus),
                            onClick = {
                                listState.delete(setOf(selectedId))
                                state.clearMessage()
                            }
                        ) {
                            Text("Delete")
                        }
                    }

                    MessageCenterMessage(
                        state = state.messageState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onClose = { state.clearMessage() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListActions(listState: MessageCenterListState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (listState.isEditing) {
            val selectedCount = listState.selectedMessageIds.size
            Button(colors = tvButtonColors(), onClick ={ if (listState.areAllMessagesSelected) listState.selectNone() else listState.selectAll() }) {
                Text(if (listState.areAllMessagesSelected) "Select none" else "Select all")
            }
            Button(colors = tvButtonColors(), enabled =selectedCount > 0, onClick = { listState.markSelectedRead() }) {
                Text("Mark read ($selectedCount)")
            }
            Button(colors = tvButtonColors(), enabled =selectedCount > 0, onClick = { listState.deleteSelected() }) {
                Text("Delete ($selectedCount)")
            }
            Button(colors = tvButtonColors(), onClick ={ listState.setEditing(false) }) {
                Text("Done")
            }
        } else {
            Button(colors = tvButtonColors(), onClick ={ listState.refresh() }) {
                Text(if (listState.isRefreshing) "Refreshing…" else "Refresh")
            }
            Button(colors = tvButtonColors(), enabled =listState.messages.isNotEmpty(), onClick = { listState.setEditing(true) }) {
                Text("Edit")
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: Message,
    listState: MessageCenterListState,
    onSelect: () -> Unit,
) {
    val isChecked = message.id in listState.selectedMessageIds

    ListItem(
        selected = message.id == listState.highlightedMessageId,
        onClick = {
            if (listState.isEditing) listState.setSelected(message.id, !isChecked) else onSelect()
        },
        leadingContent = {
            if (listState.isEditing) {
                Checkbox(checked = isChecked, onCheckedChange = null)
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (message.isRead) Color.Transparent else MaterialTheme.colorScheme.tertiary,
                            shape = CircleShape
                        )
                )
            }
        },
        headlineContent = {
            Text(message.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = listOfNotNull(message.subtitle, dateFormatter.format(message.sentDate)).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Text(message.contentType.label, style = MaterialTheme.typography.labelSmall)
        }
    )
}

private val Message.ContentType.label: String
    get() = when (this) {
        Message.ContentType.Html -> "HTML"
        Message.ContentType.Plain -> "Plain"
        is Message.ContentType.Native -> "Native"
        is Message.ContentType.Unknown -> "Unknown"
    }
