package com.urbanairship.messagecenter.compose.ui

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.urbanairship.actions.DefaultActionRunner
import com.urbanairship.actions.run
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.ui.ThomasLayoutViewFactory
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.MessageContent.WebViewState
import com.urbanairship.messagecenter.compose.ui.theme.MessageCenterTheme
import com.urbanairship.messagecenter.compose.ui.theme.MsgCenterTheme
import com.urbanairship.messagecenter.compose.ui.widget.MessageCenterWebView
import com.urbanairship.R as CoreR
import com.urbanairship.actions.Action as AutomationAction

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

    val title = (state.viewState as? State.MessageContent)?.message?.title

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
            is State.MessageContent -> {
                when(val content = viewState.content) {
                    is State.MessageContent.Content.Html -> ContentView(
                        message = viewState.message,
                        content = content,
                        onClose = onClose,
                        onAction = state::onAction
                    )
                    is State.MessageContent.Content.Native -> NativeContentView(
                        message = viewState.message,
                        content = content,
                        onClose = onClose,
                        onAction = state::onAction
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentView(
    message: Message,
    content: State.MessageContent.Content.Html,
    onClose: () -> Unit,
    onAction: ((Action) -> Unit)
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        MessageCenterWebView(
            message = message,
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
        if (content.webViewState == WebViewState.ERROR) {
            ErrorView(State.Error.Type.LOAD_FAILED) { onAction(Action.Refresh) }
        } else if (content.webViewState == WebViewState.LOADING) {
            LoadingView()
        }
    }
}

@Composable
private fun NativeContentView(
    message: Message,
    content: State.MessageContent.Content.Native,
    onClose: () -> Unit,
    onAction: ((Action) -> Unit)
) {

    onAction(Action.MarkCurrentMessageRead)

    //TODO: we probably want to move it to a view model to preserve state on refresh
    //or at least split into local properties to make it less ugly
    val args = DisplayArgs(
        payload = content.layout.layoutInfo,
        listener = object : ThomasListenerInterface {
            override fun onDismiss(cancel: Boolean) {
            }

            override fun onVisibilityChanged(
                isVisible: Boolean, isForegrounded: Boolean
            ) {
            }

            override fun onStateChanged(state: JsonSerializable) {
            }

            override fun onReportingEvent(event: ReportingEvent) {
                when(event) {
                    is ReportingEvent.Dismiss -> {
                        onClose()
                    }
                    else -> {}
                }
            }
        },
        inAppActivityMonitor = GlobalActivityMonitor.shared(LocalContext.current),
        actionRunner = object : ThomasActionRunner {
            override fun run(
                actions: Map<String, JsonValue>, state: LayoutData
            ) {
                DefaultActionRunner.run(actions, AutomationAction.Situation.AUTOMATION)
            }

        }
    )

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                ThomasLayoutViewFactory.createView(
                    context = context,
                    displayArgs = args,
                    viewId = message.id
                )?.apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                } ?: View(context)
            }
        )
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
