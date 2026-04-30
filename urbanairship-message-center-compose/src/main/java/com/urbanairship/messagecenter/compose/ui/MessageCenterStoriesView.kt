package com.urbanairship.messagecenter.compose.ui

import androidx.annotation.RestrictTo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.R as CoreR
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.ui.MessageCenterStoriesListViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterStoriesListViewModel.State
import com.urbanairship.messagecenter.compose.ui.theme.MessageCenterTheme
import com.urbanairship.messagecenter.compose.ui.theme.MsgCenterTheme
import java.util.Date
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import com.urbanairship.messagecenter.core.R as McCoreR

/**
 * Message Center Stories screen showing a horizontal list of message avatars.
 *
 * @param modifier The [Modifier] to be applied to this screen.
 * @param state The [MessageCenterStoriesState] representing the state of the screen.
 * @param onMessageSelected Callback invoked when a message story is selected.
 * @param noMessagesView View to show when there are no messages.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
public fun MessageCenterStoriesView(
    modifier: Modifier = Modifier,
    state: MessageCenterStoriesState = rememberMessageCenterStoriesState(),
    noMessagesView: @Composable () -> Unit = {},
    onMessageSelected: (Message) -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MsgCenterTheme.colors.surface
    ) {
        when (val viewState = state.viewState) {
            is State.Loading -> StoriesLoadingView()
            is State.Error -> StoriesErrorView { state.onAction(Action.Refresh) }
            is State.Content -> StoriesContentView(
                messages = viewState.messages,
                onMessageSelected = onMessageSelected,
                noMessagesView = noMessagesView,
            )
        }
    }
}

@Composable
private fun StoriesLoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MsgCenterTheme.colors.surface)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun StoriesErrorView(onRefresh: () -> Unit) {
    val typography = MsgCenterTheme.typography
    val colors = MsgCenterTheme.colors

    Column(
        modifier = Modifier
            .background(colors.surface),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(80.dp),
                painter = painterResource(McCoreR.drawable.ua_ic_message_center_info),
                contentDescription = null,
            )
            Text(
                modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 36.dp),
                text = stringResource(CoreR.string.ua_mc_failed_to_load),
                style = typography.messageCenterError,
                color = colors.messageCenterError,
                textAlign = TextAlign.Center,
            )
        }

        OutlinedButton(onClick = onRefresh) {
            Text(
                text = stringResource(CoreR.string.ua_retry_button),
                style = typography.alertButtonLabel,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun StoriesContentView(
    modifier: Modifier = Modifier,
    messages: List<Message>,
    onMessageSelected: (Message) -> Unit,
    noMessagesView: @Composable () -> Unit,
) {
    if (messages.isEmpty()) {
        noMessagesView()
        return
    }

    LazyRow(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(
            items = messages,
            key = { it.id },
        ) { message ->
            StoryItem(
                modifier = Modifier.aspectRatio(1f),
                message = message,
                onMessageSelected = onMessageSelected,
            )
        }
    }
}

@Composable
private fun StoryItem(
    modifier: Modifier,
    message: Message,
    onMessageSelected: (Message) -> Unit,
) {
    val colors = MsgCenterTheme.colors
    val options = MsgCenterTheme.options
    val borderColor = if (message.isRead) colors.divider else colors.accent

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable { onMessageSelected(message) },
        contentAlignment = Alignment.Center,
    ) {
        // Layer 1 (back): read/unread indicator — circle with border
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .border(2.dp, borderColor, CircleShape),
        )
        // Layer 2: transparent circular space (padding creates gap between border and image)
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(5.dp)
                .clip(CircleShape),
        ) {
            // Layer 3 (front): image scaled to fill inner circle, aspect ratio 1:1
            GlideImage(
                modifier = Modifier
                    .matchParentSize()
                    .aspectRatio(1f)
                    .clip(CircleShape),
                imageOptions = ImageOptions(
                    contentScale = ContentScale.FillBounds,
                ),
                imageModel = { message.listIconUrl },
                loading = { options.messageListPlaceholderIcon() },
                failure = { options.messageListPlaceholderIcon() },
            )
        }
    }
}

// region Previews
@Preview(name = "Content")
@Composable
private fun MessageCenterStoriesScreenContentPreview() {
    MessageCenterTheme {
        MessageCenterStoriesContentPreview(
            viewState = State.Content(
                messages = listOf(
                    previewMessage(id = "1", isUnread = true),
                    previewMessage(id = "2", isUnread = false),
                    previewMessage(id = "3", isUnread = true),
                ),
            ),
            onAction = {},
            onMessageSelected = {},
        )
    }
}

@Preview(name = "Loading")
@Composable
private fun MessageCenterStoriesScreenLoadingPreview() {
    MessageCenterTheme {
        MessageCenterStoriesContentPreview(
            viewState = State.Loading,
            onAction = {},
            onMessageSelected = {},
        )
    }
}

@Preview(name = "Error")
@Composable
private fun MessageCenterStoriesScreenErrorPreview() {
    MessageCenterTheme {
        MessageCenterStoriesContentPreview(
            viewState = State.Error,
            onAction = {},
            onMessageSelected = {},
        )
    }
}

@Composable
private fun MessageCenterStoriesContentPreview(
    viewState: State,
    onAction: (Action) -> Unit,
    onMessageSelected: (Message) -> Unit,
) {
    Surface(color = MsgCenterTheme.colors.surface) {
        when (viewState) {
            is State.Loading -> StoriesLoadingView()
            is State.Error -> StoriesErrorView { onAction(Action.Refresh) }
            is State.Content -> StoriesContentView(
                modifier = Modifier.height(80.dp),
                messages = viewState.messages,
                onMessageSelected = onMessageSelected,
                noMessagesView = {},
            )
        }
    }
}

private fun previewMessage(id: String, isUnread: Boolean): Message = Message(
    id = id,
    title = "Preview Message",
    bodyUrl = "https://example.com",
    sentDate = Date(System.currentTimeMillis()),
    expirationDate = null,
    isUnread = isUnread,
    extras = null,
    contentType = Message.ContentType.Html,
    messageUrl = "https://example.com",
    reporting = null,
    rawMessageJson = JsonValue.NULL,
    isDeletedClient = false,
    associatedData = null,
)

// endregion
