/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
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
        ViewModelProviders.of(this, ViewModelFactory(context!!, arguments?.getString(ARGUMENT_EVENT_ID)!!)).get(EventDetailsViewModel::class.java)
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
        setupToolbarWithNavController(R.id.toolbar);
    }

    internal class ViewModelFactory(private val context: Context, private val eventId: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EventDetailsViewModel(ServiceLocator.shared(context).getEventRepository(), eventId) as T
        }
    }
}
