/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo.tag.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDeviceInfoTagGroupsBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

class TagGroupFragment : androidx.fragment.app.Fragment() {
    private lateinit var viewModel: TagGroupViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProviders.of(this).get(TagGroupViewModel::class.java)
        val binding = UaFragmentDeviceInfoTagGroupsBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        binding.radioGroupTagGroupType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.channel) {
                viewModel.tagGroupType.set(TagGroupType.CHANNEL)
            } else {
                viewModel.tagGroupType.set(TagGroupType.NAMED_USER)
            }
        }

        when (viewModel.tagGroupType.get()) {
            TagGroupType.CHANNEL -> binding.radioGroupTagGroupType.check(R.id.channel)
            TagGroupType.NAMED_USER -> binding.radioGroupTagGroupType.check(R.id.namedUser)
            else -> binding.radioGroupTagGroupType.check(R.id.channel)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }
}
