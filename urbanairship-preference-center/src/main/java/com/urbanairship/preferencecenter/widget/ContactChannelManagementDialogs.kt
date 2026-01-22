package com.urbanairship.preferencecenter.widget

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import com.urbanairship.R
import com.urbanairship.UALog
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Action
import com.urbanairship.preferencecenter.widget.ContactChannelDialogInputView.DialogResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.urbanairship.preferencecenter.R as prefCenterR

internal fun showContactManagementAddDialog(
    context: Context,
    scope: CoroutineScope,
    item: Item.ContactManagement,
    onHandleAction: (Action) -> Unit,
    errors: Flow<String>,
    dismisses: Flow<Unit>
) {
    val themedContext = context.themed()

    val view = item.addPrompt.prompt

    val submitButtonLabel = view.submitButton.text
    val cancelButtonLabel = view.cancelButton?.text ?: themedContext.getString(R.string.ua_cancel)

    val inputView = ContactChannelDialogInputView(themedContext).apply {
        setPlatform(item.platform, view.display)
    }

    val dialog = MaterialAlertDialogBuilder(themedContext)
        .setTitle(view.display.title)
        .apply { view.display.description?.let { setMessage(it) } }
        .setView(inputView)
        .setNeutralButton(cancelButtonLabel) { dialog, _ -> dialog.cancel() }
        .setPositiveButton(submitButtonLabel) { _, _ ->
            // No-op. We override the positive button onClickListener below to prevent
            // the dialog from closing when the button is clicked.
        }
        .create()

    fun onSubmit() {
        val result = inputView.getResult()
        if (result == null || result.address.isBlank()) {
            // We shouldn't get null here, since the submit button is only enabled once
            // validation passes, but just in case...
            UALog.e { "Add contact channel dialog result was null!" }
            inputView.setError(item.platform.errorMessages.defaultMessage)
        } else {
            // Map the dialog result to an action to update the ViewModel.
            val action = when (result) {
                is DialogResult.Email -> {
                    Action.ValidateEmailChannel(item, result.address)
                }

                is DialogResult.Sms ->
                    // SMS needs to be validated by backend or a custom validation handler.
                    // The model will handle making the request and will either register the
                    // channel or show an error message via the errors flow (which is why we
                    // don't want to dismiss the dialog here).
                    Action.ValidateSmsChannel(item, result.address, result.senderId, prefix = result.prefix)
            }
            onHandleAction(action)
        }
    }

    inputView.onSubmit = { onSubmit() }

    dialog.setOnShowListener {
        // Listen for validation errors from PreferenceCenterViewModel
        errors
            .onEach { inputView.setError(it) }
            .launchIn(scope)

        // Listen for dialog dismiss requests from PreferenceCenterViewModel
        dismisses
            .onEach { dialog.dismiss() }
            .launchIn(scope)

        // Listen for validation changes from the input view
        inputView.onValidationChanged = { isValid ->
            if (isValid) {
                inputView.setError(null)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = isValid
        }

        with(dialog.getButton(AlertDialog.BUTTON_POSITIVE)) {
            // Disable the submit button. It will be re-enabled once local validation is successful.
            isEnabled = false

            // Override onClickListener to prevent dialog from closing when the button is clicked
            setOnClickListener { onSubmit() }
        }
    }

    dialog.show()
}

internal fun showContactManagementAddConfirmDialog(
    context: Context,
    message: Item.ContactManagement.ActionableMessage,
) {
    val themedContext = context.themed()

    MaterialAlertDialogBuilder(themedContext)
        .setTitle(message.title)
        .setMessage(message.description)
        .setPositiveButton(message.button.text) { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}

internal fun showContactManagementRemoveDialog(
    context: Context,
    item: Item.ContactManagement,
    channel: ContactChannel,
    onHandleAction: (Action) -> Unit
) {
    val themedContext = context.themed()

    val prompt = item.removePrompt.prompt

    val submitButtonLabel = prompt.submitButton.text
    val cancelButtonLabel = prompt.cancelButton?.text ?: themedContext.getString(R.string.ua_cancel)

    MaterialAlertDialogBuilder(themedContext)
        .setTitle(prompt.display.title)
        .apply { prompt.display.description?.let { setMessage(it) } }
        .setNeutralButton(cancelButtonLabel) { _, _ -> }
        .setNegativeButton(submitButtonLabel) { _, _ ->
            onHandleAction(Action.UnregisterChannel(channel))
        }
        .show()
}

internal fun showContactManagementResentDialog(
    context: Context,
    message: Item.ContactManagement.ActionableMessage,
) {
    val themedContext = context.themed()

    MaterialAlertDialogBuilder(themedContext)
        .setTitle(message.title)
        .setMessage(message.description)
        .setPositiveButton(message.button.text) { _, _ -> }
        .show()
}

private fun Context.themed(): Context =
    ContextThemeWrapper(this, prefCenterR.style.UrbanAirship_PreferenceCenter_Fragment)
