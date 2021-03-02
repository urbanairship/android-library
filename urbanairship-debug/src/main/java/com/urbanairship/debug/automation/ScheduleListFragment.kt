/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DividerItemDecoration
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.ScheduleData
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentAutomationListBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

class ScheduleListFragment : Fragment() {

    private lateinit var viewModel: ScheduleListViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProvider(this).get(ScheduleListViewModel::class.java)
        val binding = UaFragmentAutomationListBinding.inflate(inflater, container, false)

        val automationAdapter = ScheduleListAdapter {
            if (isResumed) {
                val args = Bundle()
                args.putString(ScheduleDetailsFragment.ARGUMENT_SCHEDULE_ID, it.id)
                when (it.type) {
                    Schedule.TYPE_IN_APP_MESSAGE -> Navigation.findNavController(binding.root).navigate(R.id.inAppScheduleDetailsFragment, args)
                    Schedule.TYPE_ACTION -> Navigation.findNavController(binding.root).navigate(R.id.actionsScheduleDetailsFragment, args)
                    Schedule.TYPE_DEFERRED -> Navigation.findNavController(binding.root).navigate(R.id.deferredScheduleDetailsFragment, args)
                }
            }
        }

        viewModel.schedules.observe(viewLifecycleOwner, Observer(automationAdapter::submitList))

        binding.apply {
            lifecycleOwner = this@ScheduleListFragment
            viewModel = this@ScheduleListFragment.viewModel
        }

        binding.recyclerView.apply {
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            adapter = automationAdapter
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }
}
