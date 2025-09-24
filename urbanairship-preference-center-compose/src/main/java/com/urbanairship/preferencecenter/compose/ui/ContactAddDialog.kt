package com.urbanairship.preferencecenter.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.urbanairship.UALog
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactManagement.Platform
import com.urbanairship.preferencecenter.data.Item.ContactManagement.PromptDisplay
import com.urbanairship.preferencecenter.data.Item.ContactManagement.SmsSenderInfo
import com.urbanairship.preferencecenter.util.airshipMarkdownToHtml
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactAddDialog(
    prompt: Item.ContactManagement.AddPrompt,
    platform: Platform,
    validator: (input: String?) -> Boolean,
    viewModel: AddContactDialogViewModel
) {
    var isValid by remember { mutableStateOf(false) }
    var inputValue by remember { mutableStateOf("") }
    var senderInfo: SmsSenderInfo? = null
    val errorText = viewModel.errors.collectAsStateWithLifecycle(null).value

    BasicAlertDialog(
        onDismissRequest = viewModel::dismiss
    ) {
        Surface(
            shape = PrefCenterTheme.shapes.contactManagementDialog,
            color = PrefCenterTheme.colors.contactManagementDialogBackground,
        ) {
            Column(Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogPadding)) {
                // Title
                Text(
                    text = prompt.prompt.display.title,
                    style = PrefCenterTheme.typography.contactManagementDialogTitle,
                    color = PrefCenterTheme.colors.contactManagementDialogTitleText,
                    modifier = Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogTitlePadding)
                )

                // Description (Optional)
                prompt.prompt.display.description?.let { text ->
                    Text(
                        text = text,
                        style = PrefCenterTheme.typography.contactManagementDialogDescription,
                        color = PrefCenterTheme.colors.contactManagementDialogDescriptionText,
                        modifier = Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogDescriptionPadding)
                    )
                }

                // Sender picker (only for SMS)
                if (platform is Platform.Sms) {
                    PhoneCountryPicker(
                        modifier = Modifier.fillMaxWidth()
                            .padding(PrefCenterTheme.dimens.contactManagementDialogInputPadding)
                            .sizeIn(minHeight = PrefCenterTheme.dimens.contactManagementDialogInputMinHeight),
                        items = platform.registrationOptions.senders,
                        inputLabel = platform.registrationOptions.countryLabel,
                        selectedItem = senderInfo ?: platform.registrationOptions.senders.first(),
                        onItemSelected = { senderInfo = it }
                    )
                }

                // Input field
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth()
                        .padding(PrefCenterTheme.dimens.contactManagementDialogInputPadding)
                        .sizeIn(minHeight = PrefCenterTheme.dimens.contactManagementDialogInputMinHeight),
                    value = inputValue,
                    onValueChange = { text ->
                        isValid = validator(text)
                        inputValue = text
                        viewModel.resetError()
                    },
                    label = { Text(platform.label()) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrefCenterTheme.colors.contactManagementDialogInputBorderFocused,
                        unfocusedBorderColor = PrefCenterTheme.colors.contactManagementDialogInputBorderUnfocused,
                        focusedLabelColor = PrefCenterTheme.colors.contactManagementDialogInputLabelFocused,
                        unfocusedLabelColor = PrefCenterTheme.colors.contactManagementDialogInputLabelUnfocused,
                    ),
                    isError = viewModel.showError,
                    supportingText = {
                        if (viewModel.showError && errorText != null) {
                            Text(
                                text = errorText,
                                style = PrefCenterTheme.typography.contactManagementDialogDescription,
                                color = PrefCenterTheme.colors.error
                            )
                        }
                    },
                    trailingIcon = {
                        if (viewModel.showError) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = stringResource(com.urbanairship.R.string.ua_content_error),
                                tint = PrefCenterTheme.colors.error
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Phone
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isValid) {
                                viewModel.submit(platform, inputValue, senderInfo)
                            }
                        }
                    ),
                )

                // Footer (Optional)
                prompt.prompt.display.footer?.let { text ->
                    Text(
                        modifier = Modifier.padding(PrefCenterTheme.dimens.contactManagementDialogFooterPadding),
                        color = PrefCenterTheme.colors.contactManagementDialogDescriptionText,
                        style = PrefCenterTheme.typography.contactManagementDialogDescription,
                        text = AnnotatedString.fromHtml(
                            htmlString = text.airshipMarkdownToHtml(),
                            linkStyles = TextLinkStyles(
                                style = SpanStyle(
                                    textDecoration = TextDecoration.Underline,
                                    color = PrefCenterTheme.colors.link
                                )
                            )
                        )
                    )
                }

                // Buttons
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = viewModel::dismiss) {
                        Text(
                            text = stringResource(com.urbanairship.R.string.ua_cancel),
                            color = PrefCenterTheme.colors.contactManagementDialogButtonLabelNeutral,
                            style = PrefCenterTheme.typography.contactManagementButtonLabel
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(
                        onClick = { viewModel.submit(platform, inputValue, senderInfo) },
                        enabled = isValid,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = PrefCenterTheme.colors.contactManagementDialogButtonLabelPositive,
                            disabledContentColor = PrefCenterTheme.colors.contactManagementDialogButtonLabelDisabled
                        )
                    ) {
                        Text(
                            text = stringResource(com.urbanairship.R.string.ua_notification_button_add),
                            style = PrefCenterTheme.typography.contactManagementButtonLabel
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneCountryPicker(
    modifier: Modifier = Modifier,
    inputLabel: String,
    items: List<SmsSenderInfo>,
    selectedItem: SmsSenderInfo,
    onItemSelected: (SmsSenderInfo) -> Unit
) {

    var isExpanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(selectedItem) }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            label = { Text(inputLabel) },
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrefCenterTheme.colors.contactManagementDialogInputBorderFocused,
                unfocusedBorderColor = PrefCenterTheme.colors.contactManagementDialogInputBorderUnfocused,
                focusedLabelColor = PrefCenterTheme.colors.contactManagementDialogInputLabelFocused,
                unfocusedLabelColor = PrefCenterTheme.colors.contactManagementDialogInputLabelUnfocused,
            ),
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            tonalElevation = 0.dp,
            containerColor = PrefCenterTheme.colors.surface,
        ) {
            items.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier.fillMaxWidth()
                        .sizeIn(minHeight = PrefCenterTheme.dimens.contactManagementDialogInputMinHeight),
                    text = {
                        Text(
                            text = option.displayName,
                            color = PrefCenterTheme.colors.contactManagementDialogTitleText,
                            style = PrefCenterTheme.typography.contactManagementDialogDropdownItem
                        )
                    },
                    onClick = {
                        selected = option
                        isExpanded = false
                        onItemSelected(option)
                    },
                )
            }
        }
    }
}

internal sealed class DialogResult {
    abstract val address: String

    data class Email(override val address: String) : DialogResult()
    data class Sms(
        override val address: String,
        val senderId: String,
        val prefix: String
    ) : DialogResult()
}

internal class AddContactDialogViewModel(
    errorsFlow: Flow<String?>,
    private val onSubmit: (DialogResult) -> Unit,
    private val onDismiss: () -> Unit
): ViewModel() {

    val errors: MutableStateFlow<String?> = MutableStateFlow(null)

    init {
        viewModelScope.launch {
            errorsFlow.collect { value ->
                errors.update { value }
            }
        }
    }

    fun resetError() {
        errors.update { null }
    }

    val showError: Boolean
        get() = errors.value != null

    val errorText: String?
        get() = errors.value

    fun dismiss() {
        onDismiss()
    }

    fun submit(
        platform: Platform,
        address: String,
        sender: SmsSenderInfo?
    ) {
        val result = generateResult(platform = platform, address = address, sender = sender)

        if (result == null || result.address.isBlank()) {
            UALog.e { "Add contact channel dialog result was null!" }
            errors.update { platform.errorMessages.defaultMessage }
            return
        }

        onSubmit(result)
    }

    private fun generateResult(
        platform: Platform,
        address: String,
        sender: SmsSenderInfo?
    ): DialogResult? {
        return when (platform) {
            is Platform.Sms -> {
                if (sender == null) { return null }

                DialogResult.Sms(
                    address = address.filter { it.isDigit() },
                    senderId = sender.senderId,
                    prefix = sender.dialingCode
                )
            }
            is Platform.Email -> DialogResult.Email(address.trim())
        }
    }
}

private fun Platform.label(): String {
    return when(this) {
        is Platform.Email -> registrationOptions.addressLabel
        is Platform.Sms -> registrationOptions.phoneLabel
    }
}

@Preview
@Composable
private fun preview() {
    PreferenceCenterTheme {
        Surface {
            ContactAddDialog(
                platform = Platform.Sms(
                    registrationOptions = Item.ContactManagement.RegistrationOptions.Sms(
                        senders = listOf(
                            SmsSenderInfo(
                                senderId = "preview sender id",
                                placeholderText = "preview placeholder text",
                                dialingCode = "+1",
                                displayName = "US",
                            )
                        ),
                        countryLabel = "Country Code",
                        phoneLabel = "Phone Number",
                        resendOptions = Item.ContactManagement.ResendOptions(
                            interval = 10,
                            message = "resend message",
                            button = Item.ContactManagement.LabeledButton(
                                text = "resend button",
                                contentDescription = "resend button content description",
                            ),
                            onSuccess = null
                        ),
                        errorMessages = Item.ContactManagement.ErrorMessages(
                            defaultMessage = "default message",
                            invalidMessage = "invalid message",
                        )
                    )
                ),
                prompt = Item.ContactManagement.AddPrompt(
                    prompt = Item.ContactManagement.AddChannelPrompt(
                        type = "sms",
                        display = PromptDisplay(
                            title = "Add a phone number",
                            description = "We'll send you a text message to verify opting in.",
                            footer = "Terms & Conditions",
                        ),
                        submitButton = Item.ContactManagement.LabeledButton(
                            text = "submit button",
                            contentDescription = "submit button content description",
                        ),
                        closeButton = Item.ContactManagement.IconButton(
                            contentDescription = "close button content description",
                        ),
                        cancelButton = Item.ContactManagement.LabeledButton(
                            text = "cancel button",
                            contentDescription = "cancel button content description",
                        ),
                        onSubmit = null,
                    ), button = Item.ContactManagement.LabeledButton(
                        text = "add button",
                        contentDescription = "add button content description",
                    )
                ),
                validator = { _ -> true },
                viewModel = AddContactDialogViewModel(emptyFlow(), {}, {})
            )
        }
    }
}
