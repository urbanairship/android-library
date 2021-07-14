package com.urbanairship.preferencecenter.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.coroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.testing.OpenForTesting
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.PreferenceCenterViewModelFactory
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OpenForTesting
class PreferenceCenterFragment : Fragment(R.layout.ua_fragment_preference_center) {
    companion object {

        /**
         * Required `String` argument specifying the ID of the Preference Center to be displayed.
         */
        const val ARG_ID: String = "pref_center_id"

        /**
         * Creates a new `PreferenceCenterFragment` instance, with [preferenceCenterId] passed as an argument.
         */
        @JvmStatic fun create(preferenceCenterId: String): PreferenceCenterFragment =
            PreferenceCenterFragment().apply {
                arguments = Bundle().apply { putString(ARG_ID, preferenceCenterId) }
            }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected val viewModelFactory: ViewModelProvider.Factory by lazy {
        PreferenceCenterViewModelFactory(
                application = requireActivity().application,
                preferenceCenterId = preferenceCenterId
        )
    }

    private val preferenceCenterId: String by lazy {
        requireNotNull(arguments?.getString(ARG_ID)) { "Missing required argument: PreferenceCenterFragment.ARG_ID" }
    }

    private val viewModel: PreferenceCenterViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(PreferenceCenterViewModel::class.java)
    }

    private val adapter: PreferenceCenterAdapter by lazy {
        PreferenceCenterAdapter()
    }

    private lateinit var views: Views

    private data class Views(
        val view: View,
        val list: RecyclerView = view.findViewById(android.R.id.list)
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views = Views(view)

        with(views) {
            list.layoutManager = LinearLayoutManager(requireContext())
            list.adapter = adapter
        }

        viewLifecycleOwner.lifecycle.coroutineScope.launch {
            viewModel.states.collect { state ->
                when (state) {
                    is State.Loading -> Snackbar.make(view, "loading...", Snackbar.LENGTH_SHORT)
                    is State.Error -> Snackbar.make(view, "error: ${state.message}", Snackbar.LENGTH_LONG)
                    is State.Content -> render(state)
                }
            }
        }
    }

    private fun render(state: State.Content) {
        activity?.title = state.title
        adapter.submitList(state.list)
    }
}
