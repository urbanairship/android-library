package com.urbanairship.messagecenter.compose.ui

import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.theme.MessageCenterTheme
import com.urbanairship.messagecenter.compose.theme.MessageViewConfig
import com.urbanairship.messagecenter.ui.view.MessageViewModel
import com.urbanairship.messagecenter.ui.view.MessageViewState
import com.urbanairship.messagecenter.ui.widget.MessageWebView
import com.urbanairship.messagecenter.ui.widget.MessageWebViewClient
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.urbanairship.R as CoreR

internal interface ComposeMessageViewModel {

    val states: StateFlow<ViewState>
    fun deleteMessage()
    fun markRead()
    fun retryLoad()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MessageView(
    messageId: String?,
    viewModelFactory: MessageViewModelFactory = MessageViewModelFactory(),
    onNavigateUp: () -> Unit
) {

    val coreViewModel: MessageViewModel = viewModel(
        modelClass = MessageViewModel::class.java,
        key = "core view model",
        factory = viewModelFactory.factory
    )

    val viewModel: ComposeMessageViewModel = viewModel {
        DefaultMessageViewModel(
            messageId = messageId,
            coreViewModel
        )
    }

    val title = when(val state = viewModel.states.collectAsState().value) {
        is ViewState.MessageRetrieved -> state.message.title
        else -> null
    }

    val eventsListener: MessageViewConfig.Listener? = MessageCenterTheme.messageViewConfig.listener

    val showError = remember { mutableStateOf<MessageViewState.Error.Type?>(null) }
    val showLoading = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { title?.let { Text(text = it) } },
                colors = MessageCenterTheme.messageViewConfig.topAppBarColors(),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp
                    ) {
                        Icon(
                           imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreR.string.ua_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.deleteMessage()
                            onNavigateUp()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(CoreR.string.ua_delete)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val state = viewModel.states.collectAsState().value

        when(state) {
            ViewState.Empty -> EmptyView()
            is ViewState.Error -> showError.value = state.error
            ViewState.Loading -> showLoading.value = true
            is ViewState.MessageRetrieved -> {
                MessageContentView(
                    modifier = Modifier.padding(padding),
                    message = state.message,
                    onStartLoading = {
                        showLoading.value = true
                        showError.value = null
                    },
                    onLoadingError = {
                        showError.value = MessageViewState.Error.Type.LOAD_FAILED
                        showLoading.value = false
                    },
                    onPageLoaded = {
                        showLoading.value = false
                        showError.value = null
                        eventsListener?.onMessageLoaded(state.message)
                    }
                )
            }
        }

        if (showLoading.value) {
            LoadingView()
        }

        showError.value?.let {
            val callback = when(it) {
                MessageViewState.Error.Type.LOAD_FAILED -> viewModel::retryLoad
                MessageViewState.Error.Type.UNAVAILABLE -> null
            }
            eventsListener?.onMessageLoadError(it)
            ErrorView(
                error = it,
                onRefresh = {
                    eventsListener?.onRetryClicked()
                    callback?.invoke()
                })
        }
    }
}

@Composable
private fun MessageContentView(
    modifier: Modifier = Modifier,
    message: Message,
    onStartLoading: () -> Unit = {},
    onLoadingError: () -> Unit,
    onPageLoaded: () -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = {
            MessageWebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setWebViewClient(object : MessageWebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        onLoadingError()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageLoaded()
                    }
                })
                onStartLoading()
                loadMessage(message)
            }
        },
    )
}

@Composable
private fun ErrorView(error: MessageViewState.Error.Type, onRefresh: (() -> Unit)? = null) {
    val text = when(error) {
        MessageViewState.Error.Type.UNAVAILABLE -> stringResource(CoreR.string.ua_mc_no_longer_available)
        MessageViewState.Error.Type.LOAD_FAILED -> stringResource(CoreR.string.ua_mc_failed_to_load)
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

private class DefaultMessageViewModel(
    private val messageId: String?,
    private val coreViewModel: MessageViewModel
): ViewModel(), ComposeMessageViewModel {

    private val _states = MutableStateFlow(
        value = if (messageId == null) ViewState.Empty else ViewState.Loading
    )

    init {
        viewModelScope.launch {
            coreViewModel.states.collect {
                val viewState = when(it) {
                    is MessageViewState.Content -> ViewState.MessageRetrieved(it.message)
                    MessageViewState.Empty -> ViewState.Empty
                    is MessageViewState.Error -> ViewState.Error(it.error)
                    MessageViewState.Loading -> ViewState.Loading
                }

                _states.emit(viewState)
            }
        }

        refresh()
    }

    override val states: StateFlow<ViewState> = _states.asStateFlow()

    private fun refresh() {
        messageId?.let(coreViewModel::loadMessage)
    }

    override fun retryLoad() {
        val message = coreViewModel.currentMessage ?: return
        viewModelScope.launch {
            _states.emit(ViewState.Loading)
            delay(100.milliseconds)
            _states.emit(ViewState.MessageRetrieved(message))
        }
    }

    override fun deleteMessage() {
        val message = coreViewModel.currentMessage ?: return
        coreViewModel.deleteMessages(message)
    }

    override fun markRead() {
        val message = coreViewModel.currentMessage ?: return
        coreViewModel.markMessagesRead(message)
    }
}

internal sealed class ViewState {
    data object Empty: ViewState()
    data object Loading: ViewState()
    data class MessageRetrieved(val message: Message): ViewState()
    data class Error(val error: MessageViewState.Error.Type): ViewState()
}
