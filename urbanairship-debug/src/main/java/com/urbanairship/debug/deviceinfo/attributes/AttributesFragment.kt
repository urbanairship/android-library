/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.attributes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import com.urbanairship.UAirship
import com.urbanairship.channel.AttributeEditor
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDeviceInfoAttributesBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

private const val DATE_PICKER_TAG = "datePicker"
private const val TIME_PICKER_TAG = "timePicker"

class AttributesFragment : Fragment() {

    companion object {
        const val TYPE_ARGUMENT_KEY = "type"
        const val CONTACT_TYPE = "contact"
        const val CHANNEL_TYPE = "channel"
    }

    val viewModel: AttributesViewModel by navGraphViewModels(R.id.ua_debug_device_info_navigation)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = UaFragmentDeviceInfoAttributesBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.handlers = this

        binding.radioGroupAttributeType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.stringButton -> viewModel.attributeType.value = AttributeType.STRING
                R.id.numberButton -> viewModel.attributeType.value = AttributeType.NUMBER
                R.id.dateButton -> viewModel.attributeType.value = AttributeType.DATE
                else -> viewModel.attributeType.value = AttributeType.NUMBER
            }
        }

        when (viewModel.attributeType.value) {
            AttributeType.STRING -> binding.radioGroupAttributeType.check(R.id.stringButton)
            AttributeType.NUMBER -> binding.radioGroupAttributeType.check(R.id.numberButton)
            AttributeType.DATE -> binding.radioGroupAttributeType.check(R.id.dateButton)
            else -> binding.radioGroupAttributeType.check(R.id.stringButton)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }

    fun showDatePicker() {
        val fragment = DatePickerFragment()
        fragment.show(parentFragmentManager, DATE_PICKER_TAG)
    }

    fun showTimePicker() {
        val fragment = TimePickerFragment()
        fragment.show(parentFragmentManager, TIME_PICKER_TAG)
    }

    fun setAttribute() {
        viewModel.setAttribute(::createEditor)
        Toast.makeText(requireContext(), requireContext().getString(R.string.ua_attribute_set), Toast.LENGTH_SHORT).show()
    }

    fun removeAttribute() {
        viewModel.removeAttribute(::createEditor)
        Toast.makeText(requireContext(), requireContext().getString(R.string.ua_attribute_removed), Toast.LENGTH_SHORT).show()
    }

    private fun createEditor(): AttributeEditor {
        return when (val type = requireArguments().getString(TYPE_ARGUMENT_KEY)) {
            CONTACT_TYPE -> UAirship.shared().contact.editAttributes()
            CHANNEL_TYPE -> UAirship.shared().channel.editAttributes()
            else -> throw IllegalArgumentException("Invalid type: $type")
        }
    }
}
