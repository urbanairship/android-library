package com.urbanairship.preferencecenter.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.preferencecenter.compose.ui.item.DescriptionItem
import com.urbanairship.preferencecenter.compose.ui.item.ItemViewHelper
import com.urbanairship.preferencecenter.compose.ui.item.SectionBreakItem
import com.urbanairship.preferencecenter.compose.ui.item.SectionItem
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.Section
import kotlinx.coroutines.flow.Flow

@Composable
public fun PreferenceCenterScreen(
    identifier: String,
    topBar: @Composable ((title: String, onNavigateUp: () -> Unit) -> Unit)? = null,
    onNavigateUp: () -> Unit
) {
    PreferenceCenterScreen(
        state = rememberPreferenceCenterState(identifier),
        topBar = topBar,
        onNavigateUp = onNavigateUp
    )
}

@Composable
public fun PreferenceCenterScreen(
    state: PreferenceCenterState,
    topBar: @Composable ((title: String, onNavigateUp: () -> Unit) -> Unit)? = null,
    onNavigateUp: () -> Unit
) {
    LaunchedEffect(true) { state.onAction(Action.Refresh) }

    Scaffold(
        topBar = {
            // Show the provided topBar if available, otherwise use the default
            topBar?.invoke(state.title, onNavigateUp)
                ?: PreferenceCenterDefaults.topBar(
                    title = state.title,
                    onNavigateUp = onNavigateUp
                )
        },
    ) { padding ->
        PreferenceCenterContent(state, padding)
    }
}

@Composable
public fun PreferenceCenterContent(
    identifier: String,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    PreferenceCenterContent(
        state = rememberPreferenceCenterState(identifier),
        contentPadding = contentPadding
    )
}

@Composable
public fun PreferenceCenterContent(
    state: PreferenceCenterState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Surface(
        color = PrefCenterTheme.colors.surface,
        modifier = Modifier.padding(contentPadding)
    ) {
        val viewState = state.viewState
        val dialog = state.dialogs

        when (viewState) {
            is ViewState.Loading -> LoadingView()
            is ViewState.Error -> ErrorView { state.onAction(Action.Refresh) }
            is ViewState.Content -> ContentView(viewState) { action -> state.onAction(action) }
        }

        dialog?.let {
            DialogDisplay(it, state.errors) { action -> state.onAction(action) }
        }
    }
}

@Composable
private fun ContentView(
    viewState: ViewState.Content,
    onAction: (Action) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!viewState.title.isNullOrBlank() || !viewState.subtitle.isNullOrBlank()) {
            item {
                ItemViewHelper.createItemView(
                    item = DescriptionItem(title = viewState.title, description = viewState.subtitle),
                    viewState = viewState,
                    onAction = onAction
                )
            }
        }

        itemsIndexed(
            items = viewState.listItems,
            key = { _, item -> item.id },
            contentType = { _, item -> item.javaClass }
        ) { index, item ->
            if (index > 1 && (item is SectionItem || item is SectionBreakItem)) {
                when (item) {
                    is SectionBreakItem -> HorizontalDivider(
                        color = PrefCenterTheme.colors.divider,
                    )
                    is SectionItem -> {
                        if (viewState.listItems[index - 1] !is SectionBreakItem) {
                            HorizontalDivider(
                                color = PrefCenterTheme.colors.divider,
                                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                            )
                        }
                    }
                    else -> Unit
                }
            }

            ItemViewHelper.createItemView(item = item, viewState = viewState, onAction = onAction)
        }
    }
}

@Composable
private fun ErrorView(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row {
            Icon(
                modifier = Modifier.size(PrefCenterTheme.dimens.errorIconSize),
                imageVector = Icons.Outlined.Info,
                tint = PrefCenterTheme.colors.alertIconTint,
                contentDescription = null
            )
        }

        Row {
            Text(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 36.dp)
                    .padding(horizontal = 56.dp),
                text = stringResource(R.string.ua_preference_center_empty),
                style = PrefCenterTheme.typography.alertTitle,
                color = PrefCenterTheme.colors.alertTitleText,
                textAlign = TextAlign.Center
            )
        }

        Row {
            OutlinedButton(onRefresh) {
                Text(
                    text = stringResource(R.string.ua_preference_center_error_retry),
                    style = PrefCenterTheme.typography.alertButtonLabel,
                    color = PrefCenterTheme.colors.accent
                )
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = PrefCenterTheme.colors.loadingIndicator
        )
    }
}

@Composable
private fun DialogDisplay(
    dialog: ContactManagerDialog,
    errors: Flow<String?>,
    onAction: (Action) -> Unit
) {
    when(dialog) {
        is ContactManagerDialog.Add -> ContactAddDialog(dialog.item, errors, onAction)
        is ContactManagerDialog.Remove -> ContactRemoveDialog(
            prompt = dialog.item.removePrompt,
            onNegativeOrDismiss = { onAction(Action.DismissDialog) },
            onPositiveAction = { onAction(Action.UnregisterChannel(dialog.channel))}
        )
        is ContactManagerDialog.ConfirmAdd -> ContactActionableMessageDialog(
            message = dialog.message,
            onDismiss = { onAction(Action.DismissDialog)}
        )
        is ContactManagerDialog.ResendConfirmation -> ContactActionableMessageDialog(
            message = dialog.message,
            onDismiss = { onAction(Action.DismissDialog)}
        )
    }
}

@Composable
private fun ContactAddDialog(
    item: Item.ContactManagement,
    errors: Flow<String?>,
    onAction: (Action) -> Unit
) {
    ContactAddDialog(
        platform = item.platform,
        prompt = item.addPrompt,
        validator = {
            when(item.platform) {
                is Item.ContactManagement.Platform.Email -> !it.isNullOrBlank()
                is Item.ContactManagement.Platform.Sms -> !it.isNullOrBlank()
            }
        },
        viewModel = AddContactDialogViewModel(
            errorsFlow = errors,
            onDismiss = { onAction(Action.DismissDialog) },
            onSubmit = { result ->
                val action = when (result) {
                    is DialogResult.Email -> Action.ValidateEmailChannel(
                        item = item,
                        address = result.address
                    )
                    is DialogResult.Sms -> Action.ValidateSmsChannel(
                        item = item,
                        address = result.address,
                        senderId = result.senderId,
                        prefix = result.prefix
                    )
                }
                onAction(action)
            }
        ),
    )
}

@Preview("Content")
@Composable
internal fun PreviewPreferenceCenterContent() {
    val state =  ViewState.Content(
        config = PreferenceCenterConfig(
            id = "test-id",
            display = CommonDisplay(name = "Display name", description = "Display description"),
            sections = listOf(
                Section.Common(
                    id = "test-section-id",
                    display = CommonDisplay(name = null, description = null),
                    items = listOf(
                        Item.ChannelSubscription(
                            id = "test-item-id-1",
                            subscriptionId = "subscription-id-1",
                            display = CommonDisplay(name = "Item 1", description = "The first item"),
                            conditions = listOf()
                        ),
                        Item.ChannelSubscription(
                            id = "test-item-id-2",
                            subscriptionId = "subscription-id-2",
                            display = CommonDisplay(name = "Item 2", description = "The second item"),
                            conditions = listOf()
                        ),
                        Item.ChannelSubscription(
                            id = "test-item-id-3",
                            subscriptionId = "subscription-id-3",
                            display = CommonDisplay(name = "Item 3", description = "The first item"),
                            conditions = listOf()
                        )
                    ),
                    conditions = listOf()
                )
            ),
        ),
        conditionState = Condition.State(false),
        title = null,
        subtitle = null,
        channelSubscriptions = emptySet(),
        contactSubscriptions = emptyMap(),
        contactChannelState = emptyMap(),
        contactChannels = emptySet()
    )

    PreferenceCenterTheme {
        PreferenceCenterScreen(
            state = rememberPreferenceCenterState(
                PreferenceCenterViewModel.forPreview(state)
            ),
            onNavigateUp = {}
        )
    }
}

@Preview("Error")
@Composable
internal fun PreviewPreferenceCenterError() {
    PreferenceCenterTheme {
        PreferenceCenterScreen(
            state = rememberPreferenceCenterState(
                PreferenceCenterViewModel.forPreview(ViewState.Error())
            ),
            onNavigateUp = {}
        )
    }
}

@Preview("Loading")
@Composable
internal fun PreviewPreferenceCenterLoading() {
    PreferenceCenterTheme {
        PreferenceCenterScreen(
            state = rememberPreferenceCenterState(
                PreferenceCenterViewModel.forPreview(ViewState.Loading)
            ),
            onNavigateUp = {}
        )
    }
}
