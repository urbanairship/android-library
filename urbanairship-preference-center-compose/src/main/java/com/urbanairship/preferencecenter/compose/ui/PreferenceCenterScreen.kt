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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.contacts.Scope
import com.urbanairship.json.jsonMapOf
import com.urbanairship.preferencecenter.compose.ui.item.DescriptionItem
import com.urbanairship.preferencecenter.compose.ui.item.ItemViewHelper
import com.urbanairship.preferencecenter.compose.ui.item.SectionBreakItem
import com.urbanairship.preferencecenter.compose.ui.item.SectionItem
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.data.Button
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.IconDisplay
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.Section
import kotlinx.coroutines.flow.Flow

/**
 * Preference Center screen with a top bar.
 *
 * @param identifier The preference center identifier.
 * @param modifier The modifier to be applied to the screen.
 * @param topBar Optional top bar composable. If null, a default top bar will be used.
 * @param onNavigateUp Optional callback to be invoked when the navigate up action is triggered.
 */
@Composable
public fun PreferenceCenterScreen(
    identifier: String,
    modifier: Modifier = Modifier,
    topBar: @Composable ((title: String, onNavigateUp: () -> Unit) -> Unit)? = null,
    onNavigateUp: () -> Unit = {}
) {
    // If in preview mode, show a preview state
    if (LocalInspectionMode.current) {
        PreferenceCenterScreen(
            rememberPreferenceCenterState(PreferenceCenterViewModel.forPreview(previewState))
        )
        return
    }

    PreferenceCenterScreen(
        state = rememberPreferenceCenterState(identifier),
        modifier = modifier,
        topBar = topBar,
        onNavigateUp = onNavigateUp
    )
}

/**
 * Preference Center screen with a top bar.
 *
 * @param state The preference center [state][PreferenceCenterState].
 * @param modifier The modifier to be applied to the screen.
 * @param topBar Optional top bar composable. If null, a default top bar will be used.
 * @param onNavigateUp Optional callback to be invoked when the navigate up action is triggered.
 */
@Composable
public fun PreferenceCenterScreen(
    state: PreferenceCenterState,
    modifier: Modifier = Modifier,
    topBar: @Composable ((title: String, onNavigateUp: () -> Unit) -> Unit)? = null,
    onNavigateUp: () -> Unit = { }
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            // Show the provided topBar if available, otherwise use the default
            topBar?.invoke(state.title, onNavigateUp)
                ?: PreferenceCenterDefaults.topBar(
                    title = state.title,
                    onNavigateUp = onNavigateUp
                )
        },
    ) { padding ->
        PreferenceCenterContent(state, Modifier.fillMaxSize(), padding)
    }
}

/**
 * Preference Center content.
 *
 * @param identifier The preference center identifier.
 * @param modifier The modifier to be applied to the content.
 * @param contentPadding Optional padding to be applied to the content.
 */
@Composable
public fun PreferenceCenterContent(
    identifier: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {

    // If in preview mode, show a preview state
    if (LocalInspectionMode.current) {
        Surface(
            color = PrefCenterTheme.colors.surface,
            modifier = modifier.padding(contentPadding)
        ) {
            ContentView(previewState) {}
        }
        return
    }

    PreferenceCenterContent(
        state = rememberPreferenceCenterState(identifier),
        modifier = modifier,
        contentPadding = contentPadding
    )
}

/**
 * Preference Center content.
 *
 * @param state The preference center [state][PreferenceCenterState].
 * @param modifier The modifier to be applied to the content.
 * @param contentPadding Optional padding to be applied to the content.
 */
@Composable
public fun PreferenceCenterContent(
    state: PreferenceCenterState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    // If in preview mode, show a preview state
    if (LocalInspectionMode.current) {
        Surface(
            color = PrefCenterTheme.colors.surface,
            modifier = modifier.padding(contentPadding)
        ) {
            when (state.viewState) {
                is ViewState.Loading -> LoadingView()
                is ViewState.Error -> ErrorView {}
                is ViewState.Content -> ContentView(previewState) {}
            }
        }
        return
    }

    LaunchedEffect(Unit) { state.onAction(Action.Refresh) }

    Surface(
        color = PrefCenterTheme.colors.surface,
        modifier = modifier.padding(contentPadding)
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
                painter = painterResource(R.drawable.ua_ic_preference_center_info_circle),
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
    PreferenceCenterTheme {
        PreferenceCenterScreen(
            rememberPreferenceCenterState(PreferenceCenterViewModel.forPreview(previewState))
        )
    }
}

@Preview("Error")
@Composable
internal fun PreviewPreferenceCenterError() {
    PreferenceCenterTheme {
        PreferenceCenterScreen(
            rememberPreferenceCenterState(PreferenceCenterViewModel.forPreview(ViewState.Error()))
        )
    }
}

@Preview("Loading")
@Composable
internal fun PreviewPreferenceCenterLoading() {
    PreferenceCenterTheme {
        PreferenceCenterScreen(
            rememberPreferenceCenterState(PreferenceCenterViewModel.forPreview(ViewState.Loading))
        )
    }
}

/** Sample Content state for use in Compose previews. */
private val previewState =  ViewState.Content(
    title = "Preference Center",
    subtitle = "Manage your notification preferences",
    config = PreferenceCenterConfig(
        id = "test-id",
        display = CommonDisplay(name = null, description = null),
        sections = listOf(
            Section.Common(
                id = "test-section-id",
                display = CommonDisplay(name = null, description = null),
                conditions = emptyList(),
                items = listOf(
                    Item.Alert(
                        id = "alert-1",
                        iconDisplay = IconDisplay(
                            icon = "placeholder",
                            name = "Push notifications are disabled",
                            description = "Enable push notifications to stay up to date.",
                        ),
                        conditions = emptyList(),
                        button = Button(
                            text = "Enable notifications",
                            contentDescription = null,
                            actions = emptyMap()
                        )
                    ),
                ),
            ),
            Section.Common(
                id = "test-section-id-2",
                display = CommonDisplay(name = "Subscription items", description = "Toggles for single delivery types"),
                conditions = emptyList(),
                items = listOf(
                    Item.ChannelSubscription(
                        id = "channel-subscription-1",
                        subscriptionId = "channel-subscription-1",
                        display = CommonDisplay(name = "Channel subscription", description = "Subscription scoped to a channel"),
                        conditions = emptyList()
                    ),
                    Item.ContactSubscription(
                        id = "contact-subscription-1",
                        subscriptionId = "contact-subscription-1",
                        display = CommonDisplay(name = "Contact subscription", description = "Subscription scoped to a contact"),
                        scopes = setOf(Scope.APP),
                        conditions = emptyList()
                    ),
                ),
            ),
            Section.Common(
                id = "test-section-id-3",
                display = CommonDisplay(name = "Subscription groups", description = "Toggles for multiple delivery types"),
                conditions = emptyList(),
                items = listOf(
                    Item.ContactSubscriptionGroup(
                        id = "contact-subscription-group-1",
                        subscriptionId = "contact-subscription-group-1",
                        display = CommonDisplay(name = "Contact subscription group", description = "Subscription group scoped to a contact"),
                        conditions = emptyList(),
                        components = listOf(
                            Item.ContactSubscriptionGroup.Component(
                                scopes = setOf(Scope.APP),
                                display = CommonDisplay(name = "App", description = null),
                            ),
                            Item.ContactSubscriptionGroup.Component(
                                scopes = setOf(Scope.EMAIL),
                                display = CommonDisplay(name = "Email", description = null),
                            ),
                            Item.ContactSubscriptionGroup.Component(
                                scopes = setOf(Scope.WEB),
                                display = CommonDisplay(name = "Web", description = null),
                            ),
                            Item.ContactSubscriptionGroup.Component(
                                scopes = setOf(Scope.SMS),
                                display = CommonDisplay(name = "SMS", description = null),
                            ),
                        ),
                    ),
                ),
            ),
            Section.Common(
                id = "test-section-id-3",
                display = CommonDisplay(name = "Contact management", description = "Add or remove contact addresses"),
                conditions = emptyList(),
                items = listOf(
                    Item.ContactManagement(
                        id = "contact-management-email",
                        platform = Item.ContactManagement.Platform.Email(
                            Item.ContactManagement.RegistrationOptions.Email(
                                placeholder = "Email address",
                                addressLabel = "Email address",
                                properties = jsonMapOf(),
                                resendOptions = Item.ContactManagement.ResendOptions(
                                    interval = 60,
                                    message = "",
                                    button = Item.ContactManagement.LabeledButton(text = "Resend", contentDescription = null),
                                    onSuccess = Item.ContactManagement.ActionableMessage(
                                        title = "Email sent",
                                        button = Item.ContactManagement.LabeledButton(text = "OK", contentDescription = null),
                                        description = "A confirmation email has been sent to your address.",
                                        contentDescription = ""
                                    ),
                                ),
                                errorMessages = Item.ContactManagement.ErrorMessages(
                                    invalidMessage = "",
                                    defaultMessage = ""
                                )
                            )
                        ),
                        display = CommonDisplay(name = "Email addresses", description = "Manage your email addresses"),
                        addPrompt = Item.ContactManagement.AddPrompt(
                            prompt = Item.ContactManagement.AddChannelPrompt(
                                type = "",
                                display = Item.ContactManagement.PromptDisplay("Add your email address", null, null),
                                submitButton = Item.ContactManagement.LabeledButton("Add", null),
                                closeButton = null,
                                cancelButton = null,
                                onSubmit = null
                            ),
                            button = Item.ContactManagement.LabeledButton(text = "Add email address", contentDescription = null)
                        ),
                        removePrompt = Item.ContactManagement.RemovePrompt(
                            prompt = Item.ContactManagement.RemoveChannelPrompt(
                                type = "",
                                display = Item.ContactManagement.PromptDisplay("Add your email address", null, null),
                                submitButton = Item.ContactManagement.LabeledButton("Add", null),
                                closeButton = null,
                                cancelButton = null,
                                onSubmit = null
                            ),
                            button = Item.ContactManagement.IconButton(null)
                        ),
                        conditions = emptyList(),
                        emptyLabel = "There are no email addresses opted-in."
                    ),
                )
            )
        ),
    ),
    conditionState = Condition.State(false),
    channelSubscriptions = setOf("channel-subscription-1"),
    contactSubscriptions = mapOf(
        "contact-subscription-group-1" to setOf(Scope.APP, Scope.WEB)
    ),
    contactChannelState = emptyMap(),
    contactChannels = emptySet()
)
