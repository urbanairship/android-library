package com.urbanairship.preferencecenter.widget

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.AttributeSet
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.text.parseAsHtml
import androidx.core.view.isVisible
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.Item.ContactManagement.PromptDisplay
import com.urbanairship.preferencecenter.data.Item.ContactManagement.RegistrationOptions
import com.urbanairship.preferencecenter.data.Item.ContactManagement.SmsSenderInfo
import com.urbanairship.preferencecenter.util.emojiFlag
import com.urbanairship.preferencecenter.util.markdownToHtml
import com.google.android.material.textfield.TextInputLayout

class ContactChannelDialogInputView@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val textInputView: TextInputLayout
    private val footerView: TextView
    private val countryPickerInputView: TextInputLayout
    private val countryPickerTextView: AutoCompleteTextView

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
                onValidationChanged?.invoke(validator(s?.toString()))
            }
        })
    }

    var onValidationChanged: ((Boolean) -> Unit)? = null

    private val adapter: ArrayAdapter<String> by lazy {
        ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line)
    }

    private var options: RegistrationOptions? = null
    private var selectedSender: SmsSenderInfo? = null

    private val validator: (input: String?) -> Boolean = { input ->
        when (options) {
            is RegistrationOptions.Email -> {
                val formatted = formatEmail(input)
                !input.isNullOrBlank() && emailRegex.matches(formatted)
            }
            is RegistrationOptions.Sms -> {
                val formatted = formatPhone(selectedSender?.dialingCode, input)
                !input.isNullOrBlank() && phoneRegex.matches(formatted)
            }
            null -> false
        }
    }

    fun setOptions(options: RegistrationOptions, display: PromptDisplay) {
        this.options = options

        when (options) {
            is RegistrationOptions.Email -> {
                setAddressLabel(options.addressLabel)
                setAddressPlaceholder(options.placeholder)
                textInputView.editText?.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            is RegistrationOptions.Sms -> {
                setAddressLabel(options.phoneLabel)
                setCountryPickerLabel(options.countryLabel)
                setCountryCodes(options.senders)
                textInputView.editText?.inputType = InputType.TYPE_CLASS_PHONE
            }
        }

        display.footer?.let { setFooter(it) }
    }

    sealed class DialogResult {
        abstract val address: String

        data class Email(override val address: String) : DialogResult()
        data class Sms(override val address: String, val senderId: String) : DialogResult()
    }

    /** Returns a pending ContactChannel. */
    fun getResult(): DialogResult? {
        val address = getFormattedAddress() ?: return null

        return when (options) {
            is RegistrationOptions.Email -> DialogResult.Email(address = address)
            is RegistrationOptions.Sms -> selectedSender?.senderId?.let { senderId ->
                DialogResult.Sms(address = address, senderId = senderId)
            }
            null -> null
        }
    }

    fun setError(error: String?) {
        textInputView.error = error
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
            countryPickerTextView.setText(formatCountryPickerItem(displayName, dialingCode), false)
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

    private fun setFooter(text: String) {
        footerView.movementMethod = LinkMovementMethod.getInstance()

        footerView.text = text.markdownToHtml().parseAsHtml()
        // TODO: use the new TextClassifier API on 26+. need to add or find a compat helper...
        footerView.autoLinkMask = Linkify.ALL

        footerView.isVisible = true
    }

    private companion object {
        private val emailRegex = """[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,64}""".toRegex()
        private val phoneRegex = """^[1-9]\d{1,14}$""".toRegex()

        private fun formatEmail(email: String?): String {
            return (email ?: "").trim()
        }

        private fun formatPhone(dialingCode: String?, phoneNumber: String?): String {
            val msisdn = (dialingCode ?: "") + (phoneNumber ?: "")
            return msisdn.filter { it.isDigit() }
        }

        private fun formatCountryPickerItem(
            countryCode: String,
            dialingCode: String
        ): String = countryCode.emojiFlag?.let { flag ->
            "$flag $dialingCode"
        } ?: dialingCode
    }
}
