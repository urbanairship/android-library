package com.urbanairship.preferencecenter.widget

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Item.ContactManagement.PromptDisplay
import com.urbanairship.preferencecenter.data.Item.ContactManagement.Platform
import com.urbanairship.preferencecenter.data.Item.ContactManagement.SmsSenderInfo
import com.urbanairship.preferencecenter.util.airshipMarkdownToHtml
import com.urbanairship.preferencecenter.util.setHtml
import com.urbanairship.util.airshipEmojiFlag
import com.google.android.material.textfield.TextInputLayout

internal class ContactChannelDialogInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val textInputView: TextInputLayout
    private val footerView: TextView
    private val countryPickerInputView: TextInputLayout
    private val countryPickerTextView: AutoCompleteTextView

    var onValidationChanged: ((Boolean) -> Unit)? = null
    var onSubmit: (() -> Unit)? = null

    private val adapter: ArrayAdapter<String> by lazy {
        ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line)
    }

    private var platform: Platform? = null
    private var selectedSender: SmsSenderInfo? = null

    private var isValid = false

    private val validator: (input: String?) -> Boolean = { input ->
        when (platform) {
            is  Platform.Email -> {
                !input.isNullOrBlank()
            }
            is Platform.Sms  -> {
                !input.isNullOrBlank()
            }
            else -> false
        }
    }

    init {
        inflate(context, R.layout.ua_contact_channel_dialog_input, this)
        textInputView = findViewById(R.id.text_input)
        footerView = findViewById(R.id.footer)
        countryPickerInputView = findViewById(R.id.country_picker_input)
        countryPickerTextView = findViewById(R.id.country_picker_text)

        textInputView.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                isValid = validator(s?.toString())
                onValidationChanged?.invoke(isValid)
            }
        })

        textInputView.editText?.apply {
            // Set the on-screen keyboard's action button to "Done"
            imeOptions = EditorInfo.IME_ACTION_DONE

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if (isValid) {
                        onSubmit?.invoke()
                    }
                    true
                } else {
                    false
                }
            }

            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                    if (isValid) {
                        onSubmit?.invoke()
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    fun setPlatform(platform: Platform, display: PromptDisplay) {
        this.platform = platform

        when (platform) {
            is Platform.Email -> {
                setAddressLabel(platform.registrationOptions.addressLabel)
                platform.registrationOptions.placeholder?.let(::setAddressPlaceholder)
                textInputView.editText?.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            is Platform.Sms -> {
                setAddressLabel(platform.registrationOptions.phoneLabel)
                setCountryPickerLabel(platform.registrationOptions.countryLabel)
                setCountryCodes(platform.registrationOptions.senders)
                textInputView.editText?.inputType = InputType.TYPE_CLASS_PHONE
            }
        }

        display.footer?.let(::setFooter)
    }

    sealed class DialogResult {
        abstract val address: String

        data class Email(override val address: String) : DialogResult()
        data class Sms(
            override val address: String,
            val senderId: String,
            val prefix: String
        ) : DialogResult()
    }

    /** Returns a pending ContactChannel. */
    fun getResult(): DialogResult? {
        val address = getFormattedAddress() ?: return null

        return when (platform) {
            is Platform.Email -> DialogResult.Email(address = address)
            is Platform.Sms -> selectedSender?.let { sender ->
                DialogResult.Sms(
                    address = address,
                    senderId = sender.senderId,
                    prefix = sender.dialingCode
                )
            }
            else -> null
        }
    }

    fun setError(error: String?) {
        textInputView.error = error
        textInputView.isErrorEnabled = error != null
        textInputView.invalidate()
        textInputView.requestLayout()
    }

    /** Returns a formatted phone number or email address. */
    private fun getFormattedAddress(): String? {
        val input = textInputView.editText?.text?.toString() ?: return null

        return when (val options = selectedSender) {
            is SmsSenderInfo -> formatPhone(options.dialingCode, input)
            else -> formatEmail(input)
        }
    }

    private fun setCountryCodes(senders: List<SmsSenderInfo>) {
        adapter.addAll(senders.map { formatCountryPickerItem(it.displayName, it.dialingCode) })
        countryPickerTextView.setAdapter(adapter)

        senders.first().apply {
            selectedSender = this
            // Set the default value with no filter, because we want to show all options in the dropdown.
            countryPickerTextView.setText(displayName, false)
            setAddressPlaceholder(placeholderText)
        }

        countryPickerTextView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val item = senders[position]
            selectedSender = item
            setAddressPlaceholder(item.placeholderText)
        }

        countryPickerInputView.isVisible = true
    }

    private fun setAddressLabel(text: String) {
        textInputView.hint = text
    }

    private fun setAddressPlaceholder(text: String) {
        textInputView.placeholderText = text
    }

    private fun setCountryPickerLabel(text: String) {
        countryPickerInputView.hint = text
    }

    private fun setFooter(formattedText: String) {
        footerView.setHtml(formattedText.airshipMarkdownToHtml())
    }

    private companion object {
        private fun formatEmail(email: String?): String {
            return (email ?: "").trim()
        }

        private fun formatPhone(dialingCode: String?, phoneNumber: String?): String {
            val msisdn = phoneNumber ?: ""
            return msisdn.filter { it.isDigit() }
        }

        private fun formatCountryPickerItem(
            countryCode: String,
            dialingCode: String
        ): String = countryCode.airshipEmojiFlag?.let { flag ->
            "$flag $dialingCode"
        } ?: dialingCode
    }
}
