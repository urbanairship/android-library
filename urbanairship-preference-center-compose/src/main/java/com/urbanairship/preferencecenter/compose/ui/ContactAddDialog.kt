package com.urbanairship.preferencecenter.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.urbanairship.UALog
import com.urbanairship.preferencecenter.core.R
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

    var isValid by remember { mutableStateOf(!viewModel.showError) }
    var inputValue by remember { mutableStateOf( "") }
    var senderInfo: SmsSenderInfo? = null
    val errorText = viewModel.errors.collectAsStateWithLifecycle(null).value

    BasicAlertDialog(
        onDismissRequest = viewModel::dismiss
    ) {
        Surface(
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .background(MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = prompt.prompt.display.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                prompt.prompt.display.description?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (platform is Platform.Sms) {
                    if (senderInfo == null) {
                        senderInfo = platform.registrationOptions.senders.first()
                    }

                    phoneCountryPicker(
                        items = platform.registrationOptions.senders,
                        countryPickerTitle = platform.registrationOptions.countryLabel,
                        initialValue = senderInfo ?: platform.registrationOptions.senders.first(),
                        onItemSelected = { selected ->
                            senderInfo = selected
                        }
                    )
                }

                val interactionSource = remember { MutableInteractionSource() }
                BasicTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                        .sizeIn(minHeight = 60.dp),
                    decorationBox = { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = inputValue,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = true,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = interactionSource,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorResource(R.color.ua_preference_center_dialog_input_outline),
                                unfocusedBorderColor = colorResource(R.color.ua_preference_center_dialog_input_outline),
                            ),
                            placeholder = {
                                Text(platform.placeholder())
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            isError = viewModel.showError,
                            label = {
                                Text(platform.placeholder())
                            },
                            supportingText = {
                                if (viewModel.showError) {
                                    Text(
                                        text = errorText ?: "",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            trailingIcon = {
                                if (viewModel.showError) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = stringResource(com.urbanairship.R.string.ua_content_error),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
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
                    value = inputValue,
                    onValueChange = { text ->
                        isValid = validator(text)
                        inputValue = text
                        viewModel.resetError()
                    },
                )

                prompt.prompt.display.footer?.let { text ->
                    Text(
                        modifier = Modifier.padding(bottom = 8.dp),
                        text = AnnotatedString.fromHtml(
                            htmlString = text.airshipMarkdownToHtml(),
                            linkStyles = TextLinkStyles(
                                style = SpanStyle(
                                    textDecoration = TextDecoration.Underline,
                                    color = Color.Blue
                                )
                            )
                        )
                    )
                }

                Row(Modifier.padding(bottom = 8.dp)) {
                    TextButton(onClick = viewModel::dismiss) {
                        Text(
                            text = stringResource(com.urbanairship.R.string.ua_cancel),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(
                        onClick = { viewModel.submit(platform, inputValue, senderInfo) },
                        enabled = isValid
                    ) {
                        Text(
                            text = stringResource(com.urbanairship.R.string.ua_notification_button_add),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun phoneCountryPicker(
    modifier: Modifier = Modifier,
    countryPickerTitle: String,
    items: List<SmsSenderInfo>,
    initialValue: SmsSenderInfo,
    onItemSelected: (SmsSenderInfo) -> Unit
) {

    var isExpanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(initialValue) }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded },
    ) {
        Column {
            Text(
                modifier = Modifier.padding(top = 4.dp).menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = true
                ),
                text = countryPickerTitle,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 30.dp)
                    .padding(end = 4.dp)
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected.displayName)

                Spacer(Modifier.weight(1f))

                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
            }
        }

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            items.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(height = 26.dp, width = 0.dp),
                    text = { Text(option.displayName) },
                    onClick = {
                        selected = option
                        isExpanded = false
                        onItemSelected(option)
                    },
                )
            }
        }
    }
    HorizontalDivider()
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
        get() {
            return errors.value != null
        }

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

        val result = generateResult(
            platform = platform,
            address = address,
            sender = sender
        )

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
            is Platform.Email -> {
                DialogResult.Email(address.trim())
            }
        }
    }
}

private fun Platform.placeholder(): String {
    return when(this) {
        is Platform.Email -> registrationOptions.addressLabel
        is Platform.Sms -> registrationOptions.phoneLabel
    }
}

@Preview("Add Phone Number")
@Composable
private fun previewAddPhoneNumber() {
    ContactAddDialog(
        platform = Platform.Sms(
            registrationOptions = Item.ContactManagement.RegistrationOptions.Sms(
                senders = listOf(
                    Item.ContactManagement.SmsSenderInfo(
                        senderId = "preview sender id",
                        placeholderText = "preview placeholder text",
                        dialingCode = "+1",
                        displayName = "US",
                    )
                ),
                countryLabel = "preview country label",
                phoneLabel = "preview phone label",
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
                    title = "sms title",
                    description = "sms description",
                    footer = "sms footer",
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
            ),
            button = Item.ContactManagement.LabeledButton(
                text = "add button",
                contentDescription = "add button content description",
            )
        ),
        validator = { _ -> true },
        viewModel = AddContactDialogViewModel(emptyFlow(), {}, {})
    )
}
