package com.urbanairship.messagecenter.compose.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.messagecenter.compose.ui.theme.MessageCenterTheme
import com.urbanairship.messagecenter.compose.ui.theme.MsgCenterTheme
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.Content.WebViewState
import com.urbanairship.messagecenter.compose.ui.widget.MessageCenterWebView
import com.urbanairship.R as CoreR

/**
 * Message Center message screen, including a top bar.
 *
 * @param modifier The modifier to be applied to the screen.
 * @param state The message center message state.
 * @param topBar Optional top bar composable. If null, a default top bar will be used.
 * @param onNavigateUp Optional callback to be invoked when the navigate up action is triggered.
 * @param onClose Optional callback to be invoked when the message is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MessageCenterMessageScreen(
    modifier: Modifier = Modifier,
    state: MessageCenterMessageState = rememberMessageCenterMessageState(),
    topBar: @Composable ((title: String?, scrollBehavior: TopAppBarScrollBehavior, onNavigateUp: () -> Unit) -> Unit)? = null,
    onNavigateUp: () -> Unit = { },
    onClose: () -> Unit = { }
) {
    @OptIn(ExperimentalMaterial3Api::class)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val title = (state.viewState as? State.Content)?.message?.title

    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(connection = scrollBehavior.nestedScrollConnection)
        ,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            topBar?.invoke(title, scrollBehavior, onNavigateUp)
                ?: MessageCenterDefaults.messageTopBar(
                    title = title,
                    onNavigateUp = { onNavigateUp() },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        MessageCenterDefaults.messageTopBarActions(
                            state = state,
                            onMessageDeleted = onClose
                        )
                    }
                )
        },
    ) { paddingValues ->
        MessageCenterMessage(
            state = state,
            modifier = Modifier.padding(paddingValues.withoutBottomPadding()),
            onClose = onClose
        )
    }
}

/**
 * Message Center message content.
 *
 * @param state The message center message state.
 * @param modifier The modifier to be applied to the content.
 * @param onClose Callback to be invoked when the message requests to be closed.
 */
@Composable
public fun MessageCenterMessage(
    state: MessageCenterMessageState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    Surface(
        modifier = modifier
    ) {
        when (val viewState = state.viewState) {
            is State.Empty -> EmptyView()
            is State.Error -> ErrorView(viewState.error) { state.onAction(Action.Refresh) }
            is State.Loading -> LoadingView()
            is State.Content -> ContentView(
                viewState = viewState,
                onClose = onClose,
                onAction = state::onAction
            )
        }
    }
}

@Composable
private fun ContentView(
    viewState: State.Content,
    onClose: () -> Unit,
    onAction: ((Action) -> Unit)
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        MessageCenterWebView(
            message = viewState.message,
            onClose = onClose,
            onPageStarted = {
                onAction(Action.UpdateWebViewState(WebViewState.LOADING))
            },
            onPageError = {
                onAction(Action.UpdateWebViewState(WebViewState.ERROR))
            },
            onPageReady = {
                onAction(Action.UpdateWebViewState(WebViewState.LOADED))
                onAction(Action.MarkCurrentMessageRead)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Show the error if we have one, otherwise show loading if we're loading
        if (viewState.webViewState == WebViewState.ERROR) {
            ErrorView(State.Error.Type.LOAD_FAILED) { onAction(Action.Refresh) }
        } else if (viewState.webViewState == WebViewState.LOADING) {
            LoadingView()
        }
    }
}

@Composable
private fun ErrorView(error: State.Error.Type, onRefresh: (() -> Unit)? = null) {
    val text = when(error) {
        State.Error.Type.UNAVAILABLE -> stringResource(CoreR.string.ua_mc_no_longer_available)
        State.Error.Type.LOAD_FAILED -> stringResource(CoreR.string.ua_mc_failed_to_load)
    }

    val colors = MsgCenterTheme.colors
    val typography = MsgCenterTheme.typography

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.messageErrorBackground),

        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row {
            Icon(
                modifier = Modifier.size(96.dp, 96.dp),
                painter = painterResource(com.urbanairship.messagecenter.core.R.drawable.ua_ic_message_center_info),
                tint = MsgCenterTheme.colors.accent,
                contentDescription = ""
            )
        }

        Row {
            Text(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 48.dp),
                text = text,
                style = typography.messageError,
                color = colors.messageError,
                textAlign = TextAlign.Center
            )
        }

        if (onRefresh != null) {
            Row {
                TextButton(onRefresh) {
                    Text(
                        text = stringResource(CoreR.string.ua_retry_button).uppercase()
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    val colors = MsgCenterTheme.colors

    Box(Modifier
        .fillMaxSize()
        .background(colors.messageLoadingBackground)
    ) {
        val content = MsgCenterTheme.options.messageLoadingView
        if (content != null) {
            content()
        } else {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = colors.accent
            )
        }
    }
}

@Composable
private fun EmptyView() {
    Box(Modifier
        .fillMaxSize()
        .background(MsgCenterTheme.colors.messageEmptyBackground)
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = stringResource(CoreR.string.ua_message_not_selected),
            color = MsgCenterTheme.colors.messageEmptyLabel
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun previewError() {
    MessageCenterTheme {
        ErrorView(State.Error.Type.UNAVAILABLE)
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun previewEmpty() {
    MessageCenterTheme {
        EmptyView()
    }
}
