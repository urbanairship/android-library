/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentAutomationDetailsBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

abstract class AutomationDetailsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = UaFragmentAutomationDetailsBinding.inflate(inflater, container, false)
        val automationDetailsAdapter = AutomationDetailsAdapter()

        createDetails().observe(viewLifecycleOwner, Observer(automationDetailsAdapter::submitList))

        binding.apply {
            lifecycleOwner = this@AutomationDetailsFragment
        }

        binding.inappMessageDetailsList.apply {
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            adapter = automationDetailsAdapter
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }

    abstract fun createDetails(): LiveData<List<AutomationDetail>>
}
