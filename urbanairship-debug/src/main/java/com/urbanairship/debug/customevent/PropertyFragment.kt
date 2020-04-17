package com.urbanairship.debug.customevent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.navigation.navGraphViewModels
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentCustomEventPropertyBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import com.urbanairship.json.JsonValue

class PropertyFragment : Fragment() {
    companion object {
        const val ARGUMENT_PROPERTY_NAME = "name"
    }

    private val customEventViewModel: CustomEventViewModel by navGraphViewModels(R.id.ua_debug_custom_event_navigation)

    private val propertyViewModel by lazy(LazyThreadSafetyMode.NONE) {
        val name = arguments?.getString(ARGUMENT_PROPERTY_NAME)
        val value = name?.let { customEventViewModel.getProperty(it) }
        ViewModelProvider(this, ViewModelFactory(name, value)).get(PropertyViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = UaFragmentCustomEventPropertyBinding.inflate(inflater, container, false)
        binding.let {
            it.handlers = this
            it.viewModel = propertyViewModel
            it.lifecycleOwner = viewLifecycleOwner
        }

        binding.radioGroupPropertyType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.stringChip -> propertyViewModel.propertyType.value = PropertyType.STRING
                R.id.numberChip -> propertyViewModel.propertyType.value = PropertyType.NUMBER
                R.id.booleanChip -> propertyViewModel.propertyType.value = PropertyType.BOOLEAN
                R.id.jsonChip -> propertyViewModel.propertyType.value = PropertyType.JSON
            }
        }

        when (propertyViewModel.propertyType.value) {
            PropertyType.STRING -> binding.radioGroupPropertyType.check(R.id.stringChip)
            PropertyType.NUMBER -> binding.radioGroupPropertyType.check(R.id.numberChip)
            PropertyType.BOOLEAN -> binding.radioGroupPropertyType.check(R.id.booleanChip)
            PropertyType.JSON -> binding.radioGroupPropertyType.check(R.id.jsonChip)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }

    fun save() {
        if (propertyViewModel.validate()) {
            propertyViewModel.initName?.let {
                customEventViewModel.removeProperty(it)
            }

            customEventViewModel.addProperty(propertyViewModel.name.value!!, propertyViewModel.value)
            cancel()
        }
    }

    fun cancel() {
        Navigation.findNavController(requireView()).popBackStack()
    }

    @Suppress("UNCHECKED_CAST")
    internal class ViewModelFactory(private val name: String?, private val value: JsonValue?) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PropertyViewModel(name, value) as T
        }
    }
}
