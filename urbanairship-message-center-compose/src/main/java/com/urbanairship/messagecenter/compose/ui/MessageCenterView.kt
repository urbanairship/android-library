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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.AirshipDispatchers
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.compose.R
import com.urbanairship.messagecenter.compose.theme.MessageCenterListConfig
import com.urbanairship.messagecenter.compose.theme.MessageCenterTheme
import com.urbanairship.messagecenter.ui.view.MessageListAction
import com.urbanairship.messagecenter.ui.view.MessageListState
import com.urbanairship.messagecenter.ui.view.MessageListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.urbanairship.R as CoreR
import com.urbanairship.messagecenter.core.R as McCoreR

public interface MessageCenterListViewModel {
    public val states: StateFlow<MessageListState>
    public val editing: StateFlow<Boolean>

    public fun setEditing(enabled: Boolean)

    public fun refresh()
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MessageCenterView(
    title: String? = null,
    messageIdToDisplay: StateFlow<String?> = MutableStateFlow(null),
    viewModelFactory: MessageCenterViewModelFactory = MessageCenterViewModelFactory(
        predicate = MessageCenter.shared().predicate
    ),
    navigateToMessageId: (String) -> Unit,
) {
    val coreViewModel: MessageListViewModel = viewModel(
        modelClass = MessageListViewModel::class.java,
        key = "core view model",
        factory = viewModelFactory.factory
    )

    val eventsListener = MessageCenterTheme.listConfig.listener
    val listViewModel: MessageCenterListViewModel = viewModel {
        DefaultMessageCenterListViewModel(
            coreViewModel = coreViewModel,
            listener = eventsListener,
            selectedMessageFlow = messageIdToDisplay,
            onNavigateToMessageId = navigateToMessageId
        )
    }

    val contentViewModel: MessageListContentViewModel = viewModel {
        DefaultListContentViewModel(
            isEditing = listViewModel.editing,
            viewModel = coreViewModel,
        )
    }

    val isEditing = listViewModel.editing.collectAsState().value
    val viewState = listViewModel.states
        .collectAsState()
        .value

    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { title?.let { Text(it) } },
                colors = MessageCenterTheme.listConfig.topBarColors(),
                actions = {
                    IconButton(
                        modifier = Modifier.semantics {
                            val stringId = if (isEditing) McCoreR.string.ua_leave_edit_mode else McCoreR.string.ua_enter_edit_mode
                            contentDescription = context.getString(stringId)
                        },
                        onClick = { listViewModel.setEditing(!isEditing) },
                        enabled = viewState is MessageListState.Content
                    ) {
                        Icon(
                            painter = if (isEditing) painterResource(R.drawable.ua_ic_edit_off_24)
                                        else painterResource(R.drawable.ua_ic_edit_24),
                            contentDescription = null
                        )
                    }

                    IconButton(
                        onClick = { showMenu = !showMenu }
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            modifier = Modifier.semantics {
                                contentDescription = context.getString(CoreR.string.ua_refresh)
                            },
                            enabled = viewState is MessageListState.Content,
                            onClick = {
                                showMenu = false
                                listViewModel.refresh()
                            },
                            text = { Text(stringResource(CoreR.string.ua_refresh)) }
                        )
                    }
                }
            )
        },
    ) { padding ->
        Surface(Modifier
            .padding(top = padding.calculateTopPadding())
        ) {
            when(viewState) {
                is MessageListState.Loading -> LoadingView()
                is MessageListState.Error -> ErrorView { listViewModel.refresh() }
                is MessageListState.Content -> MessageListView(
                    state = viewState,
                    viewModel = contentViewModel,
                    onMessageClick = {
                        coreViewModel.setHighlighted(it)
                        navigateToMessageId(it.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ErrorView(onRefresh: () -> Unit) {
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
                text = stringResource(CoreR.string.ua_mc_failed_to_load),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.secondary
                ),
                textAlign = TextAlign.Center
            )
        }

        Row {
            TextButton(onRefresh) {
                Text(
                    text = stringResource(CoreR.string.ua_retry_button).uppercase()
                )
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

internal class DefaultMessageCenterListViewModel(
    private val coreViewModel: MessageListViewModel,
    private val listener: MessageCenterListConfig.Listener? = null,
    selectedMessageFlow: StateFlow<String?>,
    onNavigateToMessageId: (String) -> Unit,
): ViewModel(), MessageCenterListViewModel {

    private val _isEditing = MutableStateFlow(false)

    override val states: StateFlow<MessageListState> = coreViewModel.states
    override val editing: StateFlow<Boolean> = _isEditing.asStateFlow()

    init {
        viewModelScope.launch {
            selectedMessageFlow.collect {
                coreViewModel.setHighlighted(it)
                it?.let { onNavigateToMessageId(it) }
            }
        }
    }

    override fun setEditing(enabled: Boolean) {
        _isEditing.update { enabled }
        listener?.onEditModeChanged(enabled)
    }

    override fun refresh() {
        coreViewModel.refreshInbox {  }
        listener?.onAction(MessageListAction.Refresh())
    }
}
