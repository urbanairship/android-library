package com.urbanairship.messagecenter.compose.ui.widget

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.theme.MessageCenterTheme
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.Action
import java.text.DateFormat
import java.util.Date
import com.skydoves.landscapist.glide.GlideImage
import com.urbanairship.R as CoreR
import com.urbanairship.messagecenter.core.R as McCoreR

@Composable
internal fun MessageListItem(
    modifier: Modifier = Modifier,
    message: Message,
    isSelected: Boolean,
    isEditing: Boolean,
    isHighlighted: Boolean,
    onAction: (Action) -> Unit
) {
    val theme = MessageCenterTheme.listConfig.listItemConfig

    val background = if (isHighlighted) {
        theme.highlightBackground ?: MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        theme.background ?: MaterialTheme.colorScheme.background
    }

    val context = LocalContext.current

    Row(
        modifier = modifier
            .sizeIn(minHeight = theme.minHeight)
            .wrapContentHeight()
            .fillMaxWidth()
            .background(background)
            .padding(theme.padding)
            .semantics {
                customActions = accessibilityActions(
                    context = context,
                    message = message,
                    isEditing = isEditing,
                    isSelected = isSelected,
                    onAction = onAction
                )
                contentDescription(context, message, isEditing, isHighlighted)
            }
        ,
    ) {
        AnimatedContent(
            targetState = isEditing,
            modifier = Modifier.padding(end = 8.dp)
        ) { targetState ->
            if (targetState) {
                Box(
                    modifier = Modifier.size(
                        if (theme.showThumbnails) 64.dp else 20.dp
                    )
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onAction(Action.SetSelected(message, it)) },
                        colors = theme.checkBoxStyle(),
                        modifier = Modifier.align(Alignment.Center)
                            .padding(top = if (theme.showThumbnails) 0.dp else 4.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(if (theme.showThumbnails) 64.dp else 20.dp)
                        .padding(
                            top = if (theme.showThumbnails) 0.dp else 4.dp,
                            start = if (theme.showThumbnails) 0.dp else 2.dp
                        )
                ) {
                    if (theme.showThumbnails) {
                        GlideImage(
                            modifier = Modifier.size(56.dp)
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(8.dp)),
                            imageModel = { message.listIconUrl },
                            loading = { theme.placeholderIcon() },
                            failure = { theme.placeholderIcon() })

                    }

                    theme.unreadIndicator(message.isRead)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                style = theme.titleStyle(),
                text = message.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            message.subtitle?.let { text ->
                Text(
                    style = theme.subtitleStyle(),
                    text = text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                style = theme.dateStyle(),
                color = MaterialTheme.colorScheme.secondary,
                text = DateFormat.getDateInstance(DateFormat.LONG).format(message.sentDate)
            )
        }
    }
}


private fun accessibilityActions(
    context: Context,
    message: Message,
    isEditing: Boolean,
    isSelected: Boolean,
    onAction: (Action) -> Unit,
): List<CustomAccessibilityAction> {
    val actions = mutableListOf(
        CustomAccessibilityAction(
            label = context.getString(CoreR.string.ua_delete),
            action = {
                onAction(Action.DeleteMessages(listOf(message)))
                true
            }
        )
    )

    if (!message.isRead) {
        actions.add(
            CustomAccessibilityAction(
                label = context.getString(McCoreR.string.ua_description_mark_read),
                action = {
                    onAction(Action.MarkMessagesRead(listOf(message)))
                    true
                }
            )
        )
    }

    if (isEditing) {
        actions.add(
            CustomAccessibilityAction(
                label = context.getString(
                    if (isSelected) McCoreR.string.ua_mc_action_unselect
                    else McCoreR.string.ua_mc_action_select
                ),
                action = {
                    onAction(Action.SetSelected(message, !isSelected))
                    true
                }
            )
        )
    }

    return actions.toList()
}

private fun contentDescription(
    context: Context,
    message: Message,
    isEditing: Boolean,
    isMessageSelected: Boolean
): String {
    val sb = StringBuilder()
    // Selected state
    if (isEditing && isMessageSelected) {
        sb.append(context.getString(McCoreR.string.ua_mc_description_state_selected))
    }
    // Read state
    if (message.isUnread) {
        sb.append(context.getString(McCoreR.string.ua_mc_description_state_unread))
    }
    // Title and date
    sb.append(
        context.getString(
            McCoreR.string.ua_mc_description_title_and_date,
            message.title,
            DateFormat.getDateInstance(DateFormat.LONG).format(message.sentDate)
        )
    )

    return sb.toString()
}

@Preview
@Composable
private fun PreviewMessageListItem() {
    val message = Message(
        id = "id",
        title = "Great Deals Just for You!",
        bodyUrl = "https://www.urbanairship.com",
        sentDate = Date(System.currentTimeMillis()),
        expirationDate = null,
        isUnread = true,
        extras = mapOf(
            "com.urbanairship.listing.field1" to "All the best prices on all the best stuff. Don't miss out!"
        ),
        messageUrl = "https://www.urbanairship.com",
        reporting = null,
        rawMessageJson = JsonValue.NULL,
        isDeletedClient = false,
    )

    MessageListItem(
        modifier = Modifier.padding(8.dp),
        message = message,
        isSelected = false,
        isEditing = false,
        isHighlighted = true,
        onAction = {}
    )
}
