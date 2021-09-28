/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo.tag.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDeviceInfoTagGroupsBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

class TagGroupFragment : Fragment() {
    private lateinit var viewModel: TagGroupViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProvider(this).get(TagGroupViewModel::class.java)
        val binding = UaFragmentDeviceInfoTagGroupsBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        binding.radioGroupIdentifierType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.channelChip -> viewModel.tagGroupType.set(TagGroupType.CHANNEL)
                R.id.namedUserChip -> viewModel.tagGroupType.set(TagGroupType.CONTACT)
            }
        }

        when (viewModel.tagGroupType.get()) {
            TagGroupType.CHANNEL -> binding.radioGroupIdentifierType.check(R.id.channelChip)
            TagGroupType.CONTACT -> binding.radioGroupIdentifierType.check(R.id.namedUserChip)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }
}
