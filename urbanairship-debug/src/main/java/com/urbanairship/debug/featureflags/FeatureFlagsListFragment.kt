package com.urbanairship.debug.featureflags

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DividerItemDecoration
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentFeatureFlagsListBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

class FeatureFlagsListFragment : Fragment(R.layout.ua_fragment_feature_flags_list) {
    private val viewModel: FeatureFlagsViewModel by lazy {
        ViewModelProvider(this, FeatureFlagsViewModel.ViewModelFactory())[FeatureFlagsViewModel::class.java]
    }
    private val adapter: FeatureFlagsAdapter by lazy { FeatureFlagsAdapter() }

    private lateinit var binding: UaFragmentFeatureFlagsListBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = UaFragmentFeatureFlagsListBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            lifecycleOwner = this@FeatureFlagsListFragment
            viewModel = viewModel

            list.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            list.adapter = adapter
        }

        viewModel.featureFlags
            .observe(viewLifecycleOwner) { adapter.submitList(it) }

        adapter.listener = { featureFlag ->
            val args = Bundle().apply {
                putParcelable(FeatureFlagsDetailFragment.JSON, featureFlag.toJsonValue())
            }
            Navigation.findNavController(binding.root)
                .navigate(R.id.featureFlagsDetailFragment, args)
        }

        setupToolbarWithNavController(R.id.toolbar)
    }
}
