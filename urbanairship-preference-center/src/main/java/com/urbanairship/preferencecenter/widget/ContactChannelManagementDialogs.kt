package com.urbanairship.preferencecenter.widget

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getString
import androidx.lifecycle.lifecycleScope
import com.urbanairship.R
import com.urbanairship.UALog
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.ui.PreferenceCenterFragment
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Action
import com.urbanairship.preferencecenter.widget.ContactChannelDialogInputView.DialogResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.urbanairship.preferencecenter.R as prefCenterR

internal fun PreferenceCenterFragment.showContactManagementAddDialog(
    item: Item.ContactManagement,
    onHandleAction: (Action) -> Unit,
    errors: Flow<String>,
    dismisses: Flow<Unit>
) {
    val context = requireContext().themed()

    val view = item.addPrompt.prompt

    val submitButtonLabel = view.submitButton.text
    val cancelButtonLabel = view.cancelButton?.text ?: context.getString(R.string.ua_cancel)

    val inputView = ContactChannelDialogInputView(context).apply {
        setOptions(item.registrationOptions, view.display)
    }

    val dialog = MaterialAlertDialogBuilder(context)
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
            inputView.setError(item.registrationOptions.errorMessages.defaultMessage)
        } else {
            // Map the dialog result to an action and pass it back to the Fragment to update
            // the ViewModel.
            val action = when (result) {
                is DialogResult.Email -> {
                    dialog.dismiss()
                    // Email is assumed to be valid after passing our local regex check
                    Action.RegisterChannel.Email(item, result.address)
                }

                is DialogResult.Sms ->
                    // SMS passed local validation, but needs to be validated by backend or
                    // a custom validation handler. The model will handle making the request
                    // and will either register the channel or show an error message via the
                    // errors flow (which is why we don't want to dismiss the dialog here).
                    Action.ValidateSmsChannel(item, result.address, result.senderId)
            }
            onHandleAction(action)
        }
    }

    inputView.onSubmit = { onSubmit() }

    dialog.setOnShowListener {
        // Listen for validation errors from PreferenceCenterViewModel
        errors
            .onEach { inputView.setError(it) }
            .launchIn(lifecycleScope)

        // Listen for dialog dismiss requests from PreferenceCenterViewModel
        dismisses
            .onEach { dialog.dismiss() }
            .launchIn(lifecycleScope)

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

internal fun PreferenceCenterFragment.showContactManagementAddConfirmDialog(
    message: Item.ContactManagement.ActionableMessage,
) {
    val context = requireContext().themed()

    MaterialAlertDialogBuilder(context)
        .setTitle(message.title)
        .setMessage(message.description)
        .setPositiveButton(message.button.text) { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}

internal fun PreferenceCenterFragment.showContactManagementRemoveDialog(
    item: Item.ContactManagement,
    channel: ContactChannel,
    onHandleAction: (Action) -> Unit
) {
    val context = requireContext().themed()

    val prompt = item.removePrompt.prompt

    val submitButtonLabel = prompt.submitButton.text
    val cancelButtonLabel = prompt.cancelButton?.text ?: context.getString(R.string.ua_cancel)

    MaterialAlertDialogBuilder(context)
        .setTitle(prompt.display.title)
        .apply { prompt.display.description?.let { setMessage(it) } }
        .setNeutralButton(cancelButtonLabel) { _, _ -> }
        .setNegativeButton(submitButtonLabel) { _, _ ->
            onHandleAction(Action.UnregisterChannel(channel))
        }
        .show()
}

internal fun PreferenceCenterFragment.showContactManagementResentDialog(
    message: Item.ContactManagement.ActionableMessage,
) {
    val context = requireContext().themed()

    MaterialAlertDialogBuilder(context,)
        .setTitle(message.title)
        .setMessage(message.description)
        .setPositiveButton(message.button.text) { _, _ -> }
        .show()
}

private fun Context.themed(): Context =
    ContextThemeWrapper(this, prefCenterR.style.UrbanAirship_PreferenceCenter_Fragment)
