package com.urbanairship.messagecenter.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.urbanairship.messagecenter.compose.theme.MessageCenterTheme
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.Content.WebViewState
import com.urbanairship.messagecenter.compose.ui.widget.MessageCenterWebView
import com.urbanairship.R as CoreR

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
            modifier = Modifier.padding(paddingValues),
            onClose = onClose
        )
    }
}

@Composable
public fun MessageCenterMessage(
    state: MessageCenterMessageState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    Surface(
        color = Color.Red,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MessageCenterTheme.errorViewConfig.padding ?: PaddingValues(0.dp))
            .background(MessageCenterTheme.errorViewConfig.backgroundColor ?: MaterialTheme.colorScheme.background),

        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row {
            Icon(
                modifier = Modifier.size(96.dp, 96.dp),
                imageVector = Icons.Outlined.Info,
                tint = MaterialTheme.colorScheme.secondary,
                contentDescription = ""
            )
        }

        Row {
            Text(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 48.dp),
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.secondary
                ),
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
    Box(Modifier
        .fillMaxSize()
        .background(MessageCenterTheme.loadingViewConfig.backgroundColor ?: MaterialTheme.colorScheme.background)
    ) {
        val content = MessageCenterTheme.loadingViewConfig.spinner
        if (content != null) {
            content()
        } else {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptyView() {
    Box(Modifier
        .fillMaxSize()
        .background(MessageCenterTheme.loadingViewConfig.backgroundColor ?: MaterialTheme.colorScheme.background)
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = stringResource(CoreR.string.ua_message_not_selected)
        )
    }
}
