package com.urbanairship.messagecenter.compose.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.R
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.ui.theme.MessageCenterTheme
import com.urbanairship.messagecenter.compose.ui.theme.MsgCenterTheme
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.State
import com.urbanairship.messagecenter.compose.ui.widget.MessageListItem
import com.urbanairship.R as CoreR
import com.urbanairship.messagecenter.core.R as McCoreR

/**
 * Message Center List Screen, including a top bar and edit toolbar.
 *
 * @param modifier The [Modifier] to be applied to this screen.
 * @param state The [MessageCenterListState] representing the state of the screen.
 * @param topBar Optional top bar composable. If null, a default top bar will be used.
 * @param onNavigateUp Callback invoked when the navigate up action is triggered.
 * @param onMessageSelected Callback invoked when a message is selected.
 */
@Composable
public fun MessageCenterListScreen(
    modifier: Modifier = Modifier,
    state: MessageCenterListState = rememberMessageCenterListState(),
    topBar: @Composable ((onNavigateUp: () -> Unit) -> Unit)? = null,
    onNavigateUp: () -> Unit = { },
    onMessageSelected: (Message) -> Unit,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        modifier = modifier,
        topBar = {
            val title = MsgCenterTheme.options.messageCenterListTitle ?: stringResource(R.string.ua_message_center_title)

            @OptIn(ExperimentalMaterial3Api::class)
            topBar?.invoke(onNavigateUp)
                ?: MessageCenterDefaults.listTopBar(
                    title = title,
                    isEditing = state.isEditing,
                    onNavigateUp = onNavigateUp,
                    actions = { MessageCenterDefaults.listTopBarActions(state) },
                )
        },
        bottomBar = {
            EditToolbar(
                isVisible = state.isEditing,
                areAllMessagesSelected = state.areAllMessagesSelected,
                selectedCount = state.selectedCount,
                onAction = state::onAction
            )
        }
    ) { paddingValues ->
        MessageCenterListContent(
            state = state,
            onMessageSelected = onMessageSelected,
            modifier = Modifier.padding(paddingValues.withoutBottomPadding()),
        )
    }
}

/**
 * Message Center List Content (includes an edit toolbar, but no top bar).
 *
 * @param state The [MessageCenterListState] representing the state of the screen.
 * @param onMessageClick Callback invoked when a message is clicked.
 * @param modifier The [Modifier] to be applied to this screen.
 */
@Composable
public fun MessageCenterList(
    state: MessageCenterListState = rememberMessageCenterListState(),
    onMessageClick: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            EditToolbar(
                isVisible = state.isEditing,
                areAllMessagesSelected = state.areAllMessagesSelected,
                selectedCount = state.selectedCount,
                onAction = state::onAction
            )
        }
    ) { paddingValues ->
        MessageCenterListContent(
            state = state,
            onMessageSelected = onMessageClick,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun MessageCenterListContent(
    state: MessageCenterListState,
    onMessageSelected: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MsgCenterTheme.colors.surface
    ) {
        when (val viewState = state.viewState) {
            is State.Loading -> LoadingView()
            is State.Error -> ErrorView { state.onAction(Action.Refresh()) }
            is State.Content -> ContentView(
                viewState = viewState,
                onMessageClick = { message ->
                    state.onAction(Action.SetHighlighted(message.id))
                    onMessageSelected(message)
                },
                onAction = state::onAction,
            )
        }
    }
}

@Composable
private fun ContentView(
    viewState: State.Content,
    onMessageClick: (Message) -> Unit,
    onAction: (Action) -> Unit,
) {
    val colors = MsgCenterTheme.colors
    val options = MsgCenterTheme.options
    val dimens = MsgCenterTheme.dimensions

    PullToRefresh(
        viewState = viewState,
        onAction = onAction
    )
     {
        if (viewState.messages.isEmpty()) {
            EmptyView()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(viewState.messages.size) { index ->
                    val message = viewState.messages[index]

                    MessageListItem(
                        modifier = Modifier.clickable { onMessageClick(message) },
                        message = message,
                        isSelected = viewState.isSelected(message),
                        isEditing = viewState.isEditing,
                        isHighlighted = viewState.isHighlighted(message),
                        onAction = onAction
                    )

                    if (options.messageCenterDividerEnabled && index < viewState.messages.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = dimens.messageCenterDividerInset.start,
                                end = dimens.messageCenterDividerInset.end
                            ).background(MsgCenterTheme.colors.divider)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefresh(
    viewState: State.Content,
    onAction: (Action) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = viewState.isRefreshing,
        onRefresh = { onAction(Action.Refresh()) },
        state = state,
        content = content,
        indicator = {
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = viewState.isRefreshing,
                state = state,
                containerColor = MsgCenterTheme.colors.messageCenterPullToRefreshBackground,
                color = MsgCenterTheme.colors.messageCenterPullToRefresh
            )
        },
    )
}

@Composable
private fun ErrorView(onRefresh: () -> Unit) {
    val typography = MsgCenterTheme.typography
    val colors = MsgCenterTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().background(colors.surface),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row {
            Icon(
                modifier = Modifier.size(96.dp),
                painter = painterResource(com.urbanairship.messagecenter.core.R.drawable.ua_ic_message_center_info),
                //tint = MessageCenterTheme.colors.alertIconTint,
                contentDescription = null
            )
        }

        Row {
            Text(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 36.dp)
                    .padding(horizontal = 56.dp),
                text = stringResource(CoreR.string.ua_mc_failed_to_load),
                style = typography.messageCenterError,
                color = colors.messageCenterError,
                textAlign = TextAlign.Center
            )
        }

        Row {
            OutlinedButton(onRefresh) {
                Text(
                    text = stringResource(CoreR.string.ua_retry_button),
                    style = typography.alertButtonLabel,
                    color = colors.accent
                )
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize().background(MsgCenterTheme.colors.surface)) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            //color = MessageCenterTheme.colors.loadingIndicator
        )
    }
}

@Composable
private fun EmptyView() {
    val override = MsgCenterTheme.options.messageCenterEmptyListMessage
    if (override != null) {
        override()
    } else {
        Row(
            modifier = Modifier.fillMaxSize().background(MsgCenterTheme.colors.surface),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                style = MsgCenterTheme.typography.emptyViewMessage,
                text = stringResource(CoreR.string.ua_empty_message_list),
            )
        }
    }
}

@Composable
private fun EditToolbar(
    isVisible: Boolean,
    areAllMessagesSelected: Boolean,
    selectedCount: Int,
    onAction: (Action) -> Unit
) {
    val colors = MsgCenterTheme.colors
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        val context = LocalContext.current

        Box(modifier = Modifier.fillMaxWidth().background(colors.messageCenterEditBar)) {
            Surface(
                color = colors.messageCenterEditBar,
                contentColor = colors.messageCenterEditBarContent,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.Center)
                    .padding(
                        top = 8.dp,
                        bottom = 8.dp,
                    )
            ) {
                Row(horizontalArrangement = Arrangement.Center) {
                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            onAction(
                                if (areAllMessagesSelected) Action.SelectNone else Action.SelectAll
                            )
                        }) {
                        val text = if (areAllMessagesSelected) {
                            stringResource(CoreR.string.ua_select_none)
                        } else {
                            stringResource(CoreR.string.ua_select_all)
                        }
                        Text(text)
                    }

                    Spacer(Modifier.width(4.dp))

                    TextButton(onClick = { onAction(Action.MarkSelectedMessagesRead) }) {
                        Text(
                            text = toolbarItemLabel(context, CoreR.string.ua_mark_read, selectedCount)
                        )
                    }

                    if (MsgCenterTheme.options.canDeleteMessages) {
                        Spacer(Modifier.width(4.dp))

                        TextButton(onClick = { onAction(Action.DeleteSelectedMessages) }) {
                            Text(
                                text = toolbarItemLabel(context, CoreR.string.ua_delete, selectedCount)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

private fun toolbarItemLabel(context: Context, @StringRes titleResId: Int, count: Int = 0): String =
    if (count == 0) {
        // No count, just load the title: "Mark as read", "Delete", etc.
        context.getString(titleResId)
    } else {
        // We have a count. Format the title with the count: "Mark as read (3)", "Delete (5)", etc.
        context.getString(
            McCoreR.string.ua_edit_toolbar_item_title_with_count,
            context.getString(titleResId),
            count
        )
    }

@Preview
@Composable
private fun preview() {
    MessageCenterTheme {
        MessageCenterListScreen {  }
    }
}
