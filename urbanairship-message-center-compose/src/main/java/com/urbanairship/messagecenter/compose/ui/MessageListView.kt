package com.urbanairship.messagecenter.compose.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urbanairship.AirshipDispatchers
import com.urbanairship.Provider
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.theme.MessageCenterListConfig
import com.urbanairship.messagecenter.compose.theme.MessageCenterTheme
import com.urbanairship.messagecenter.ui.view.MessageListAction
import com.urbanairship.messagecenter.ui.view.MessageListState
import java.text.DateFormat
import com.skydoves.landscapist.glide.GlideImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.urbanairship.R as CoreR
import com.urbanairship.messagecenter.core.R as McCoreR
import com.urbanairship.messagecenter.ui.view.MessageListViewModel as CoreViewModel

internal interface MessageListContentViewModel {

    val areAllMessagesSelected: Boolean
    val isEditing: StateFlow<Boolean>
    val toolbarLabels: StateFlow<ToolbarLabels?>

    val messageSelectionState: StateFlow<Map<String, Boolean>>

    fun setContextProvider(provider: Provider<Context>)

    fun updateMessageSelection(message: Message, isSelected: Boolean)
    fun markRead(message: Message)

    fun onSelectAll()
    fun onUnselectAll()
    fun onMarkReadSelected()
    fun onDeleteSelected()

    fun delete(message: Message)
    fun refresh()
}

internal data class ToolbarLabels(
    val select: String,
    val markRead: String,
    val delete: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageListView(
    state: MessageListState.Content,
    viewModel: MessageListContentViewModel,
    onMessageClick: (Message) -> Unit
) {
    val theme = MessageCenterTheme.listConfig

    val isEditing by viewModel.isEditing.collectAsState()

    val context = LocalContext.current
    viewModel.setContextProvider { context }

    val eventsListener = MessageCenterTheme.listConfig.listener

    Scaffold(
        bottomBar = {
            editToolbar(isEditing, viewModel)
        }
    ) { paddingValues ->
        PullToRefreshBox(
            modifier = Modifier
                .padding(bottom = paddingValues.calculateBottomPadding())
                .background(
                    MessageCenterTheme.listConfig.background ?: MaterialTheme.colorScheme.background
                ),
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh
        ) {
            if (state.messages.isEmpty()) {
                EmptyView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.messages.size) { index ->
                        val message = state.messages[index]
                        MessageListItem(
                            modifier = Modifier.clickable {
                                onMessageClick(message)
                                eventsListener?.onShowMessage(message)
                            },
                            message = message,
                            isEditing = isEditing,
                            viewModel = viewModel,
                            isHighlighted = state.highlightedMessageId == message.id
                        )

                        if (theme.itemDividersEnabled && index < state.messages.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(
                                    start = theme.itemDividerInset.start,
                                    end = theme.itemDividerInset.end
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun editToolbar(
    isVisible: Boolean,
    viewModel: MessageListContentViewModel
) {

    val labels = viewModel.toolbarLabels.collectAsState().value

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it }
        ),
        exit = slideOutVertically(
            targetOffsetY = { it }
        )
    ) {
        BottomAppBar(
            containerColor = MessageCenterTheme.listConfig.editBarContainerColor(),
            contentColor = MessageCenterTheme.listConfig.editBarContentColor(),
            actions = {
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        if (viewModel.areAllMessagesSelected) {
                            viewModel.onUnselectAll()
                        } else {
                            viewModel.onSelectAll()
                        }
                    }
                ) {
                    Text(labels?.select ?: "")
                }

                TextButton(
                    onClick = { viewModel.onMarkReadSelected() }
                ) {
                    Text(labels?.markRead ?: "")
                }

                TextButton(
                    onClick = { viewModel.onDeleteSelected() }
                ) {
                    Text(labels?.delete ?: "")
                }
                Spacer(Modifier.weight(1f))
            }
        )
    }
}

@Composable
private fun MessageListItem(
    modifier: Modifier = Modifier,
    message: Message,
    viewModel: MessageListContentViewModel,
    isEditing: Boolean,
    isHighlighted: Boolean
    ) {

    val theme = MessageCenterTheme.listConfig.listItemConfig

    val background = if (isHighlighted) {
        theme.highlightBackground ?: MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        theme.background ?: MaterialTheme.colorScheme.background
    }

    val context = LocalContext.current

    val isChecked = viewModel.messageSelectionState
        .collectAsState()
        .value[message.id]
        ?: false

    Row(
        modifier = modifier
            .sizeIn(minHeight = theme.minHeight)
            .fillMaxWidth()
            .background(background)
            .padding(theme.padding)
            .semantics {
                customActions = accessibilityActions(
                    context = context,
                    message = message,
                    viewModel = viewModel,
                    isEditing = isEditing,
                    isSelected = isChecked
                )
                contentDescription(context, message, isEditing, isHighlighted)
            }
        ,
    ) {
        AnimatedContent(isEditing) {
            when(it) {
                true -> {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = {
                            viewModel.updateMessageSelection(message, it)
                        },
                        colors = theme.checkBoxStyle()
                    )
                }
                false -> {
                    theme.unreadIndicator(message.isRead)
                    Spacer(Modifier.padding(end = 8.dp))

                    if (theme.showThumbnails) {
                        GlideImage(
                            modifier = Modifier.size(64.dp),
                            imageModel = { message.listIconUrl  },
                            loading = { theme.placeholderIcon() },
                            failure = { theme.placeholderIcon() }
                        )
                    }
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
    viewModel: MessageListContentViewModel,
    isEditing: Boolean,
    isSelected: Boolean
): List<CustomAccessibilityAction> {
    val result = mutableListOf(
        CustomAccessibilityAction(
            label = context.getString(CoreR.string.ua_delete),
            action = {
                viewModel.delete(message)
                true
            }
        )
    )

    if (!message.isRead) {
        result.add(
            CustomAccessibilityAction(
                label = context.getString(McCoreR.string.ua_description_mark_read),
                action = {
                    viewModel.markRead(message)
                    true
                }
            )
        )
    }

    if (isEditing) {
        result.add(
            CustomAccessibilityAction(
                label = context.getString(
                    if (isSelected) McCoreR.string.ua_mc_action_unselect
                    else McCoreR.string.ua_mc_action_select
                ),
                action = {
                    viewModel.updateMessageSelection(message, !isSelected)
                    true
                }
            )
        )
    }

    return result.toList()
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

internal class DefaultListContentViewModel(
    override val isEditing: StateFlow<Boolean>,
    private val viewModel: CoreViewModel,
    private val eventsListener: MessageCenterListConfig.Listener? = null,
) : ViewModel(), MessageListContentViewModel {

    private val messagesSelectionMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val allMessages = MutableStateFlow<List<Message>>(emptyList())

    private val toolbarLabelsFlow = MutableStateFlow<ToolbarLabels?>(null)
    private var contextProvider: Provider<Context>? = null

    override val messageSelectionState: StateFlow<Map<String, Boolean>> = messagesSelectionMap.asStateFlow()

    override val areAllMessagesSelected: Boolean
        get() = messagesSelectionMap.value.isNotEmpty()
                && messagesSelectionMap.value.values.all { it }

    init {
        viewModelScope.launch {
            isEditing
                .filter { it }
                .collect {
                    messagesSelectionMap.update {
                        it.mapValues { (_, isSelected) -> false }
                    }
                }
        }

        val state = viewModel.states.value as? MessageListState.Content
        state?.let(::processState)

        viewModelScope.launch {
            viewModel.states.collect(::processState)
        }
    }

    private fun processState(state: MessageListState) {
        when(state) {
            is MessageListState.Content -> {
                allMessages.update { state.messages }

                messagesSelectionMap.update { current ->
                    val result = current.toMutableMap()

                    //remove all messages that are no longer in the list
                    result.keys
                        .filter { id -> !state.messages.any { it.id == id } }
                        .forEach { result.remove(it) }

                    //add all new messages with unselected mark
                    state.messages
                        .filter { !current.keys.contains(it.id) }
                        .forEach { result[it.id] = false }

                    result.toMap()
                }
            }
            else -> {
                allMessages.update { emptyList() }
                messagesSelectionMap.update { emptyMap() }
            }
        }

        updateLabels()
    }

    override fun setContextProvider(provider: Provider<Context>) {
        this.contextProvider = provider
        updateLabels()
    }

    private fun updateLabels() {
        val context = contextProvider?.get() ?: return

        val selectText = if (areAllMessagesSelected) {
            context.getString(CoreR.string.ua_select_none)
        } else {
            context.getString(CoreR.string.ua_select_all)
        }

        val count = messagesSelectionMap.value.count { it.value }

        toolbarLabelsFlow.update {
            ToolbarLabels(
                select = selectText,
                markRead = getItemLabelString(context, CoreR.string.ua_mark_read, count),
                delete = getItemLabelString(context, CoreR.string.ua_delete, count)
            )
        }
    }

    private fun getItemLabelString(context: Context, @StringRes titleResId: Int, count: Int = 0): String =
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

    override val toolbarLabels: StateFlow<ToolbarLabels?> = toolbarLabelsFlow.asStateFlow()

    override fun updateMessageSelection(
        message: Message, isSelected: Boolean
    ) {
        messagesSelectionMap.update {
            it
                .toMutableMap()
                .apply { set(message.id, isSelected) }
                .toMap()
        }
        updateLabels()
    }

    override fun markRead(message: Message) {
        val messages = listOf(message)
        viewModel.markMessagesRead(messages)
        eventsListener?.onAction(MessageListAction.MarkMessagesRead(messages))
    }

    override fun onSelectAll() {
        messagesSelectionMap.update { it.keys.associateWith { true } }
        updateLabels()
    }

    override fun onUnselectAll() {
        messagesSelectionMap.update { it.keys.associateWith { false } }
        updateLabels()
    }

    override fun onMarkReadSelected() {
        val selected = messagesSelectionMap.value
            .filter { it.value }
            .keys
            .mapNotNull { allMessages.value.find { message -> message.id == it } }
            .toList()

        viewModel.markMessagesRead(selected)
        eventsListener?.onAction(MessageListAction.MarkMessagesRead(selected))
    }

    override fun onDeleteSelected() {
        val selected = messagesSelectionMap.value
            .filter { it.value }
            .keys
            .mapNotNull { allMessages.value.find { message -> message.id == it } }
            .toList()

        viewModel.deleteMessages(selected)
        eventsListener?.onAction(MessageListAction.DeleteMessages(selected))
    }

    override fun delete(message: Message) {
        val messages = listOf(message)
        viewModel.deleteMessages(messages)
        eventsListener?.onAction(MessageListAction.DeleteMessages(messages))
    }

    override fun refresh() {
        viewModel.refreshInbox {  }
        eventsListener?.onAction(MessageListAction.Refresh())
    }
}

@Composable
private fun EmptyView() {
    val override = MessageCenterTheme.listConfig.overrideEmptyListMessage
    if (override != null) {
        override()
    } else {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                text = stringResource(CoreR.string.ua_empty_message_list),
            )
        }
    }
}

@Composable
@Preview("Content")
private fun previewContent() {
    MessageListView(
        state = MessageListState.Content(
            messages = listOf(
                Message(
                    id = "message-1",
                    title = "message - 1 title",
                    bodyUrl = "message - 1 bodyUrl",
                    sentDate = java.util.Date(),
                    expirationDate = null,
                    isUnread = true,
                    extras = mapOf(
                        "com.urbanairship.listing.field1" to "message subtitle"
                    ),
                    messageUrl = "message - 1 messageUrl",
                    reporting = null,
                    rawMessageJson = JsonValue.NULL,
                    isUnreadClient = true,
                    isDeletedClient = false
                ),
                Message(
                    id = "message-2",
                    title = "message - 2 title",
                    bodyUrl = "message - 2 bodyUrl",
                    sentDate = java.util.Date(),
                    expirationDate = null,
                    isUnread = false,
                    extras = null,
                    messageUrl = "message - 2 messageUrl",
                    reporting = null,
                    rawMessageJson = JsonValue.NULL,
                    isUnreadClient = true,
                    isDeletedClient = false
                )
            ),
            isRefreshing = false
        ),
        viewModel = object : MessageListContentViewModel {
            override val isEditing: StateFlow<Boolean> = MutableStateFlow(true)
            override val toolbarLabels: StateFlow<ToolbarLabels> = MutableStateFlow(ToolbarLabels("Select", "Mark as read", "Delete"))
            override val messageSelectionState: StateFlow<Map<String, Boolean>> = MutableStateFlow(
                mapOf("message-2" to true)
            )

            override val areAllMessagesSelected: Boolean = false
            override fun updateMessageSelection(message: Message, isSelected: Boolean) { }
            override fun refresh() { }
            override fun markRead(message: Message) { }
            override fun delete(message: Message) { }
            override fun onSelectAll() { }
            override fun onUnselectAll() { }
            override fun onMarkReadSelected() { }
            override fun onDeleteSelected() { }
            override fun setContextProvider(provider: Provider<Context>) { }
        },
        onMessageClick = {}
    )
}

@Composable
@Preview("Empty")
private fun previewEmpty() {
    MessageListView(
        state = MessageListState.Content(
            messages = emptyList(),
            isRefreshing = false
        ),
        viewModel = object : MessageListContentViewModel {
            override val isEditing: StateFlow<Boolean> = MutableStateFlow(false)
            override val toolbarLabels: StateFlow<ToolbarLabels> = MutableStateFlow(ToolbarLabels("Select", "Mark as read", "Delete"))
            override val messageSelectionState: StateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap())

            override val areAllMessagesSelected: Boolean = false
            override fun refresh() { }
            override fun updateMessageSelection(message: Message, isSelected: Boolean) { }
            override fun markRead(message: Message) { }
            override fun delete(message: Message) { }
            override fun onSelectAll() { }
            override fun onUnselectAll() { }
            override fun onMarkReadSelected() { }
            override fun onDeleteSelected() { }
            override fun setContextProvider(provider: Provider<Context>) { }
        },
        onMessageClick = {}
    )
}
