package com.urbanairship.debug.preferencecenter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentPrefCenterListBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import com.urbanairship.debug.preferencecenter.PrefCentersViewModel.ViewModelFactory
import com.urbanairship.preferencecenter.PreferenceCenter

class PrefCentersListFragment : Fragment(R.layout.ua_fragment_pref_center_list) {

    private val viewModel: PrefCentersViewModel by lazy {
        ViewModelProvider(this, ViewModelFactory())[PrefCentersViewModel::class.java]
    }
    private val adapter: PrefCentersAdapter by lazy { PrefCentersAdapter() }

    private lateinit var binding: UaFragmentPrefCenterListBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = UaFragmentPrefCenterListBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            lifecycleOwner = this@PrefCentersListFragment
            viewModel = viewModel

            list.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            list.adapter = adapter
        }

        viewModel.prefCenters
            .observe(viewLifecycleOwner) { adapter.submitList(it) }

        adapter.listener = { prefCenter ->
            PreferenceCenter.shared().open(prefCenter.id)
        }

        setupToolbarWithNavController(R.id.toolbar)
    }
}
