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
import com.urbanairship.Logger
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.Trigger
import com.urbanairship.automation.actions.Actions
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentActionsListBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonMatcher
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher

class ActionsScheduleListFragment : Fragment() {

    private lateinit var viewModel: ActionsScheduleViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProvider(this).get(ActionsScheduleViewModel::class.java)
        val binding = UaFragmentActionsListBinding.inflate(inflater, container, false)

        val automationAdapter = ActionsScheduleListAdapter {
            if (isResumed) {
                val args = Bundle()
                args.putString(ActionsScheduleDetailsFragment.ARGUMENT_SCHEDULE_ID, it.id)
                Navigation.findNavController(binding.root).navigate(R.id.actionsScheduleDetailsFragment, args)
            }
        }

        viewModel.schedules.observe(viewLifecycleOwner, Observer(automationAdapter::submitList))

        binding.apply {
            lifecycleOwner = this@ActionsScheduleListFragment
            viewModel = this@ActionsScheduleListFragment.viewModel
        }

        binding.actionsRecyclerView.apply {
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