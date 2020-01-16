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
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProviders
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDeviceInfoAttributesBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import kotlinx.android.synthetic.main.ua_fragment_device_info_attributes.*
import kotlinx.android.synthetic.main.ua_fragment_event_list.view.*

class DeviceInfoAttributesFragment  : androidx.fragment.app.Fragment() {
    private lateinit var viewModel: DeviceInfoAttributesViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProviders.of(this).get(DeviceInfoAttributesViewModel::class.java)
        val binding = UaFragmentDeviceInfoAttributesBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        binding.radioGroupAttributeType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.numberButton) {
                viewModel.attributeType.set(AttributeType.NUMBER)
            } else {
                viewModel.attributeType.set(AttributeType.STRING)
            }
        }

        when(viewModel.attributeType.get()) {
            AttributeType.STRING -> binding.radioGroupAttributeType.check(R.id.stringButton)
            AttributeType.NUMBER -> binding.radioGroupAttributeType.check(R.id.numberButton)
            else -> binding.radioGroupAttributeType.check(R.id.stringButton)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }
}