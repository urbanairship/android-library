/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo.attributes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDeviceInfoAttributesBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

class AttributesFragment  : androidx.fragment.app.Fragment() {
    private lateinit var viewModel: AttributesViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProviders.of(this).get(AttributesViewModel::class.java)
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