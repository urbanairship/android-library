package com.urbanairship.preferencecenter.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.compose.ui.item.DescriptionItem
import com.urbanairship.preferencecenter.compose.ui.item.ItemViewHelper
import com.urbanairship.preferencecenter.compose.ui.item.SectionBreakItem
import com.urbanairship.preferencecenter.compose.ui.item.SectionItem
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
internal fun PreferenceCenterView(
    viewModel: PreferenceCenterViewModel,
    onBackButton: () -> Unit
) {

    LaunchedEffect(true) {
        viewModel.handle(Action.Refresh)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.ua_preference_center_label))
                },
                navigationIcon = {
                    IconButton(onClick = onBackButton) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
    ) { padding ->
        Surface(Modifier
            .padding(padding)
        ) {
            val viewState = viewModel.states
                .collectAsState()
                .value

            val dialog = viewModel.displayDialog.collectAsStateWithLifecycle(null).value

            when(viewState) {
                is ViewState.Loading -> LoadingView()
                is ViewState.Error -> ErrorView { viewModel.handle(Action.Refresh) }
                is ViewState.Content -> ContentView(viewState, viewModel)
            }

            dialog?.let { displayDialog(it, viewModel) }
        }
    }
}

@Composable
private fun displayDialog(
    dialog: ContactManagerDialog,
    viewModel: PreferenceCenterViewModel
) {
    when(dialog) {
        is ContactManagerDialog.Add -> {
            dialogContactAdd(dialog.item, viewModel = viewModel)
        }
        is ContactManagerDialog.Remove -> {
            ContactDialogHelper.removeChannel(
                prompt = dialog.item.removePrompt,
                onNegativeOrDismiss = { viewModel.handle(Action.DismissDialog) },
                onPositiveAction = { viewModel.handle(Action.UnregisterChannel(dialog.channel))}
            )
        }
        is ContactManagerDialog.ConfirmAdd -> {
            ContactDialogHelper.actionableMessageDialog(
                message = dialog.message,
                onDismiss = { viewModel.handle(Action.DismissDialog)}
            )
        }
        is ContactManagerDialog.ResendConfirmation -> {
            ContactDialogHelper.actionableMessageDialog(
                message = dialog.message,
                onDismiss = { viewModel.handle(Action.DismissDialog)}
            )
        }
    }
}

@Composable
private fun dialogContactAdd(
    item: Item.ContactManagement,
    viewModel: PreferenceCenterViewModel) {

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
            errorsFlow = viewModel.errors,
            onDismiss = { viewModel.handle(Action.DismissDialog) },
            onSubmit = { result ->
                val action = when (result) {
                    is DialogResult.Email -> {
                        Action.ValidateEmailChannel(
                            item = item,
                            address = result.address)
                    }
                    is DialogResult.Sms -> {
                        Action.ValidateSmsChannel(
                            item = item,
                            address = result.address,
                            senderId = result.senderId,
                            prefix = result.prefix)
                    }
                }

                viewModel.handle(action)
            }
        ),
    )
}

@Composable
private fun ContentView(
    state: ViewState.Content,
    viewModel: PreferenceCenterViewModel
) {
    Column(Modifier.fillMaxWidth()) {
        if (!state.title.isNullOrBlank() || !state.subtitle.isNullOrEmpty()) {
            ItemViewHelper.createItemView(
                item = DescriptionItem(
                    title = state.title,
                    description = state.subtitle
                ),
                viewState = state,
                model = viewModel)
        }

        state.listItems.forEachIndexed { index, item ->
            if (index > 1 && (item is SectionItem || item is SectionBreakItem)) {
                when(item) {
                    is SectionBreakItem -> HorizontalDivider()
                    is SectionItem -> {
                        if (state.listItems[index - 1] !is SectionBreakItem) {
                            HorizontalDivider(Modifier.padding(bottom = 22.dp))
                        }
                    }
                    else -> {}
                }

            }

            ItemViewHelper.createItemView(item, state, model = viewModel)
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
                modifier = Modifier.size(96.dp, 96.dp),
                imageVector = Icons.Outlined.Info,
                tint = MaterialTheme.colorScheme.secondary,
                contentDescription = ""
            )
        }

        Row {
            Text(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 48.dp)
                    .padding(horizontal = 56.dp),
                text = stringResource(R.string.ua_preference_center_empty),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.secondary
                ),
                textAlign = TextAlign.Center
            )
        }

        Row {
            TextButton(onRefresh) {
                Text(
                    text = stringResource(R.string.ua_preference_center_error_retry).uppercase()
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
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Preview("PC Content")
@Composable
internal fun PreviewPreferenceCenterContent() {
    PreferenceCenterView(
        viewModel = object : PreferenceCenterViewModel {
            override val states: StateFlow<ViewState>
                get() = MutableStateFlow(ViewState.Content(
                    config = PreferenceCenterConfig(
                        id = "test-id",
                        sections = listOf(),
                        display = CommonDisplay(
                            name = "Display name",
                            description = "Display description"
                        )
                    ),
                    conditionState = Condition.State(false),
                    title = "Title",
                    subtitle = "Description",
                    channelSubscriptions = emptySet(),
                    contactSubscriptions = emptyMap(),
                    contactChannelState = emptyMap(),
                    contactChannels = emptySet()
                ))

            override fun handle(action: Action) { }
            override val displayDialog: StateFlow<ContactManagerDialog?> = MutableStateFlow(null)
            override val errors: Flow<String?> = emptyFlow()

        },
        onBackButton = {}
    )
}

@Preview("On Error")
@Composable
internal fun PreviewPreferenceCenterError() {
    PreferenceCenterView(
        viewModel = object : PreferenceCenterViewModel {
            override val states: StateFlow<ViewState>
                get() = MutableStateFlow(ViewState.Error())

            override fun handle(action: Action) { }
            override val displayDialog: StateFlow<ContactManagerDialog?> = MutableStateFlow(null)
            override val errors: Flow<String?> = emptyFlow()

        },
        onBackButton = {}
    )
}

@Preview("On Loading")
@Composable
internal fun PreviewPreferenceCenterLoading() {
    PreferenceCenterView(
        viewModel = object : PreferenceCenterViewModel {
            override val states: StateFlow<ViewState>
                get() = MutableStateFlow(ViewState.Loading)

            override fun handle(action: Action) { }
            override val displayDialog: StateFlow<ContactManagerDialog?> = MutableStateFlow(null)
            override val errors: Flow<String?> = emptyFlow()

        },
        onBackButton = {}
    )
}

private sealed class DisplayDialog {
    data object None : DisplayDialog()
    data class ContactAdd(
        val item: Item.ContactManagement
    ) : DisplayDialog()
    data object ContactConfirm : DisplayDialog()
    data object ContactRemove : DisplayDialog()
    data object ContactResend : DisplayDialog()
    data object Error : DisplayDialog()
}
