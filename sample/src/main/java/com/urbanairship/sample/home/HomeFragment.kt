/* Copyright Airship and Contributors */
package com.urbanairship.sample.home

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation.findNavController
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ClipboardAction
import com.urbanairship.actions.tags.AddTagsAction
import com.urbanairship.sample.R
import com.urbanairship.sample.databinding.FragmentHomeBinding

/**
 * Fragment that displays the channel ID.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        setupWithNavController(toolbar, findNavController(view))

        val binding = FragmentHomeBinding.bind(view)
        with(binding) {
            channelId.setOnClickListener {
                ActionRunRequest.createRequest(ClipboardAction.DEFAULT_REGISTRY_NAME)
                    .setValue(binding.channelId.text).run()

                ActionRunRequest.createRequest(AddTagsAction.DEFAULT_REGISTRY_NAME)
                    .setValue("Neat")
                    .run()
            }

        }

        viewModel.channelId.observe(viewLifecycleOwner) {
            binding.channelId.text = it
        }
    }
}
