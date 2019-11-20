/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.lifecycle.ViewModelProviders
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDeviceInfoAttributesBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

class DeviceInfoAttributesFragment  : androidx.fragment.app.Fragment() {
    private lateinit var viewModel: DeviceInfoAttributesViewModel

    private lateinit var  keyEditText: EditText
    private lateinit var valueEditText: EditText
    private lateinit var setButton: RadioButton
    private lateinit var removeButton: RadioButton
    private lateinit var applyButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProviders.of(this).get(DeviceInfoAttributesViewModel::class.java)
        val binding = UaFragmentDeviceInfoAttributesBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this

        this.keyEditText = binding.keyEditText
        this.valueEditText = binding.valueEditText
        this.setButton = binding.setButton
        this.removeButton = binding.removeButton
        this.applyButton = binding.applyButton

        initEditTexts()
        initButtons()

        return binding.getRoot()
    }

    private fun clearFields() {
        keyEditText.text.clear()
        valueEditText.text.clear()
    }

    private fun initButtons() {
        // Set operation will appear as the default
        setButton.isChecked = true

        applyButton.setOnClickListener {
            applyAttributesAndUpdateView()
        }
    }

    private fun toastApplyStatus(success:Boolean) {
        if (view == null) return

        if (!success) {
            Toast.makeText(view!!.context, view!!.context.getString(R.string.ua_toast_attributes_invalid_message), Toast.LENGTH_SHORT)
                    .show()
            return
        }

        var message = view!!.context.getString(R.string.ua_toast_attributes_success_message) +
                "\n" + view!!.context.getString(R.string.ua_attributes_key) + ": " + keyEditText.text.toString()

        if (!removeButton.isChecked) {
            message += "\n" + view!!.context.getString(R.string.ua_attributes_value) + ": " + valueEditText.text.toString()
        }

        Toast.makeText(view!!.context, message, Toast.LENGTH_SHORT)
                .show()
    }

    private fun applyAttributesAndUpdateView() {
        val success = viewModel.updateAttributes(removeButton.isChecked, keyEditText.text.toString(), valueEditText.text?.toString())

        toastApplyStatus(success)

        if (success) {
            clearFields()
        }
    }

    private fun initEditTexts() {
        keyEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyAttributesAndUpdateView()
            }
            false
        }

        keyEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    applyAttributesAndUpdateView()
                }
            }
            false
        }

        valueEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyAttributesAndUpdateView()
            }
            false
        }

        valueEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    applyAttributesAndUpdateView()
                }
            }
            false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }
}