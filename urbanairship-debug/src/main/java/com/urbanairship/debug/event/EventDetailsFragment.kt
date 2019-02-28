/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.event

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.urbanairship.debug.databinding.FragmentEventDetailsBinding

/**
 * Event detail fragment.
 */
class EventDetailsFragment : Fragment() {

    companion object {
        const val ARGUMENT_EVENT_ID = "EVENT_ID"

        fun newInstance(eventId: String): EventDetailsFragment {
            val arguments = Bundle()
            arguments.putString(ARGUMENT_EVENT_ID, eventId)
            val fragment = EventDetailsFragment()
            fragment.arguments = arguments
            return fragment
        }
    }

    private lateinit var binding: FragmentEventDetailsBinding

    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProviders.of(this, ViewModelFactory(context!!, arguments?.getString(ARGUMENT_EVENT_ID)!!)).get(EventDetailsViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.binding = FragmentEventDetailsBinding.inflate(inflater, container, false)
        with(binding) {
            viewModel = this@EventDetailsFragment.viewModel
            lifecycleOwner = this@EventDetailsFragment
        }

        return binding.root
    }

    internal class ViewModelFactory(private val context: Context, private val eventId: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EventDetailsViewModel(ServiceLocator.shared(context).getEventRepository(), eventId) as T
        }
    }
}
