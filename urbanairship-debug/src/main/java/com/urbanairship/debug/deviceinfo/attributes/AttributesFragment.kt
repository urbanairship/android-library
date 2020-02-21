/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.attributes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDeviceInfoAttributesBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

private const val DATE_PICKER_TAG = "datePicker"
private const val TIME_PICKER_TAG = "timePicker"

class AttributesFragment : Fragment() {
    private lateinit var viewModel: AttributesViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProviders.of(requireActivity()).get(AttributesViewModel::class.java)
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
        fragment.show(requireFragmentManager(), DATE_PICKER_TAG)
    }

    fun showTimePicker() {
        val fragment = TimePickerFragment()
        fragment.show(requireFragmentManager(), TIME_PICKER_TAG)
    }

    fun setAttribute() {
        viewModel.setAttribute()
        Toast.makeText(requireContext(), requireContext().getString(R.string.ua_attribute_set), Toast.LENGTH_SHORT).show()
    }

    fun removeAttribute() {
        viewModel.removeAttribute()
        Toast.makeText(requireContext(), requireContext().getString(R.string.ua_attribute_removed), Toast.LENGTH_SHORT).show()
    }
}
