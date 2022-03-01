package com.urbanairship.debug.contact.open

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
import com.urbanairship.debug.databinding.UaFragmentOpenIdentifierBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

class IdentifierFragment : Fragment() {
    private val openChannelViewModel: OpenChannelViewModel by navGraphViewModels(R.id.ua_debug_contact_navigation)

    private val identifierViewModel by lazy(LazyThreadSafetyMode.NONE) {
        val key = arguments?.getString("key")
        val value = key?.let { openChannelViewModel.getIdentifier(it) }
        ViewModelProvider(this, ViewModelFactory(key, value)).get(IdentifierViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = UaFragmentOpenIdentifierBinding.inflate(inflater, container, false)
        binding.let {
            it.handlers = this
            it.viewModel = identifierViewModel
            it.lifecycleOwner = viewLifecycleOwner
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }

    fun save() {
        if (identifierViewModel.validate()) {
            identifierViewModel.initKey?.let {
                openChannelViewModel.removeIdentifier(it)
            }

            openChannelViewModel.addIdentifier(identifierViewModel.key.value!!, identifierViewModel.value.value!!)
            cancel()
        }
    }

    fun cancel() {
        Navigation.findNavController(requireView()).popBackStack()
    }

    @Suppress("UNCHECKED_CAST")
    internal class ViewModelFactory(private val key: String?, private val value: String?) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return IdentifierViewModel(key, value) as T
        }
    }
}