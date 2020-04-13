/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.urbanairship.debug.R
import com.urbanairship.debug.ServiceLocator
import com.urbanairship.debug.databinding.UaFragmentEventDetailsBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

/**
 * Event detail fragment.
 */
class EventDetailsFragment : Fragment() {

    companion object {
        const val ARGUMENT_EVENT_ID = "eventId"
    }

    private lateinit var binding: UaFragmentEventDetailsBinding

    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this, ViewModelFactory(requireContext(), arguments?.getString(ARGUMENT_EVENT_ID)!!)).get(EventDetailsViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.binding = UaFragmentEventDetailsBinding.inflate(inflater, container, false)
        with(binding) {
            viewModel = this@EventDetailsFragment.viewModel
            lifecycleOwner = this@EventDetailsFragment
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }

    internal class ViewModelFactory(private val context: Context, private val eventId: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EventDetailsViewModel(ServiceLocator.shared(context).getEventRepository(), eventId) as T
        }
    }
}
