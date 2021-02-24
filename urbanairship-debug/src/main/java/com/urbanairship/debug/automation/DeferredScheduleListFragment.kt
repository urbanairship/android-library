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
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDeferredListBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

class DeferredScheduleListFragment : Fragment() {

    private lateinit var viewModel: DeferredScheduleViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProvider(this).get(DeferredScheduleViewModel::class.java)
        val binding = UaFragmentDeferredListBinding.inflate(inflater, container, false)

        val automationAdapter = DeferredScheduleListAdapter {
            if (isResumed) {
                val args = Bundle()
                args.putString(DeferredScheduleDetailsFragment.ARGUMENT_SCHEDULE_ID, it.id)
                Navigation.findNavController(binding.root).navigate(R.id.deferredScheduleDetailsFragment, args)
            }
        }

        viewModel.schedules.observe(viewLifecycleOwner, Observer(automationAdapter::submitList))

        binding.apply {
            lifecycleOwner = this@DeferredScheduleListFragment
            viewModel = this@DeferredScheduleListFragment.viewModel
        }

        binding.deferredRecyclerView.apply {
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