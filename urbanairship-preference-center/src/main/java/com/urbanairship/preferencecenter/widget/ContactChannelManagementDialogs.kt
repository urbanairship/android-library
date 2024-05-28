package com.urbanairship.preferencecenter.widget

import androidx.appcompat.app.AlertDialog
import com.urbanairship.R
import com.urbanairship.UALog
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.ui.PreferenceCenterFragment
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Action
import com.urbanairship.preferencecenter.widget.ContactChannelDialogInputView.DialogResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal fun PreferenceCenterFragment.showContactManagementAddDialog(
    item: Item.ContactManagement,
    onHandleAction: (Action) -> Unit
) {
    val view = item.addPrompt.prompt

    val cancelButtonLabel = view.cancelButton?.text ?: getString(R.string.ua_cancel)
    val submitButtonLabel = view.submitButton?.text ?: getString(R.string.ua_notification_button_add)

    val inputView = ContactChannelDialogInputView(requireContext()).apply {
        setOptions(item.registrationOptions, view.display)
    }

    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(view.display.title)
        .apply { view.display.body?.let { setMessage(it) } }
        .setView(inputView)
        .setNeutralButton(cancelButtonLabel) { dialog, _ -> dialog.cancel() }
        .setPositiveButton(submitButtonLabel) { dialog, _ ->
            val result = inputView.getResult()
            if (result == null) {
                // We shouldn't get null here, since the submit button is only enabled once
                // validation passes, but just in case...
                UALog.e { "Add contact channel dialog result was null!" }

                val errorMessage = item.registrationOptions.errorMessages?.defaultMessage
                // TODO: do we have a default error message? add to strings if not...
                    ?: "Something went wrong! Please try again later."
                inputView.setError(errorMessage)

                return@setPositiveButton
            }

            val action = view.onSuccess?.let {
                Action.ConfirmAddChannel(item, result)
            } ?: when (result) {
                is DialogResult.Email ->
                    Action.RegisterChannel.Email(result.address)
                is DialogResult.Sms ->
                    Action.RegisterChannel.Sms(result.address, result.senderId)
            }

            onHandleAction(action)

            dialog.dismiss()
        }
        .create()

    dialog.show()
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

    inputView.onValidationChanged = { isValid ->
        if (isValid) {
            inputView.setError(null)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = isValid
    }
}

internal fun PreferenceCenterFragment.showContactManagementAddConfirmDialog(
    message: Item.ContactManagement.ActionableMessage,
    result: DialogResult,
    onHandleAction: (Action) -> Unit
) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(message.title)
        .setMessage(message.description)
        .setPositiveButton(message.button.text) { dialog, _ ->
            when (result) {
                is DialogResult.Email ->
                    Action.RegisterChannel.Email(result.address)
                is DialogResult.Sms ->
                    Action.RegisterChannel.Sms(result.address, result.senderId)
            }.let {
                onHandleAction(it)
            }

            dialog.dismiss()
        }
        .show()
}

internal fun PreferenceCenterFragment.showContactManagementRemoveDialog(
    item: Item.ContactManagement,
    channel: ContactChannel,
    onHandleAction: (Action) -> Unit
) {
    val prompt = item.removePrompt.prompt

    val cancelButtonLabel = prompt.cancelButton?.text ?: getString(R.string.ua_cancel)
    // TODO: add "add" and "remove" to strings so we can localize them, or find a better default...
    val submitButtonLabel = prompt.submitButton?.text ?: getString(R.string.ua_delete)

    MaterialAlertDialogBuilder(requireContext())
        .setTitle(prompt.display.title)
        .apply { prompt.display.body?.let { setMessage(it) } }
        .setNeutralButton(cancelButtonLabel) { dialog, _ -> dialog.cancel() }
        .setNegativeButton(submitButtonLabel) { dialog, _ ->
            onHandleAction(Action.UnregisterChannel(channel))
            dialog.dismiss()
        }
        .show()
}
