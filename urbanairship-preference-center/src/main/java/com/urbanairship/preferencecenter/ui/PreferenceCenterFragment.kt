package com.urbanairship.preferencecenter.ui

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ButtonClick
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ChannelSubscriptionChange
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactManagementAddClick
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactManagementRemoveClick
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactSubscriptionChange
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactSubscriptionGroupChange
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Action
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Effect
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.PreferenceCenterViewModelFactory
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State
import com.urbanairship.preferencecenter.widget.SectionDividerDecoration
import com.urbanairship.preferencecenter.widget.showContactManagementAddConfirmDialog
import com.urbanairship.preferencecenter.widget.showContactManagementAddDialog
import com.urbanairship.preferencecenter.widget.showContactManagementRemoveDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

    /**
     * Listener to override Preference Center display behavior.
     */
    fun interface OnDisplayPreferenceCenterListener {

        /**
         * Called when a Preference Center title and description will be displayed.
         *
         * @param title Title of the Preference Center.
         * @param description Description of the Preference Center.
         * @return `true` if the title and description were displayed, otherwise `false` to trigger the default display as an item at the top of the list.
         */
        fun onDisplayPreferenceCenter(title: String?, description: String?): Boolean
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected val viewModelFactory: ViewModelProvider.Factory by lazy {
        PreferenceCenterViewModelFactory(preferenceCenterId)
    }

    private val preferenceCenterId: String by lazy {
        requireNotNull(arguments?.getString(ARG_ID)) { "Missing required argument: PreferenceCenterFragment.ARG_ID" }
    }

    private val viewModel: PreferenceCenterViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[PreferenceCenterViewModel::class.java]
    }

    @VisibleForTesting
    protected val viewModelScopeProvider: () -> CoroutineScope = { viewModel.viewModelScope }

    private val adapter: PreferenceCenterAdapter by lazy {
        PreferenceCenterAdapter(scopeProvider = viewModelScopeProvider)
    }

    private lateinit var views: Views

    private var onDisplayListener: OnDisplayPreferenceCenterListener? = null

    private data class Views(
        val view: View,
        val list: RecyclerView = view.findViewById(R.id.list),
        val loading: ViewGroup = view.findViewById(R.id.loading),
        val error: ViewGroup = view.findViewById(R.id.error),
        val errorMessage: TextView = error.findViewById(R.id.error_text),
        val errorRetryButton: Button = error.findViewById(R.id.error_button)
    ) {
        fun showContent() {
            error.visibility = View.GONE
            loading.visibility = View.GONE

            list.visibility = View.VISIBLE
        }

        fun showError() {
            list.visibility = View.GONE
            loading.visibility = View.GONE

            error.visibility = View.VISIBLE
        }

        fun showLoading() {
            list.visibility = View.GONE
            error.visibility = View.GONE

            loading.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val themedContext = ContextThemeWrapper(requireContext(), R.style.UrbanAirship_PreferenceCenter_Fragment)
        val themedInflater = inflater.cloneInContext(themedContext)
        return super.onCreateView(themedInflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views = Views(view)

        with(views) {
            list.adapter = adapter
            list.layoutManager = LinearLayoutManager(requireContext())
            list.addItemDecoration(SectionDividerDecoration(requireContext(), list::isAnimating))
            list.setHasFixedSize(true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.states.collect(::render)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.effects.collect(::handle)
        }

        adapter.itemEvents
            .map { event ->
                when (event) {
                    is ChannelSubscriptionChange ->
                        Action.PreferenceItemChanged(event.item, event.isChecked)
                    is ContactSubscriptionChange ->
                        Action.ScopedPreferenceItemChanged(event.item, event.scopes, event.isChecked)
                    is ContactSubscriptionGroupChange ->
                        Action.ScopedPreferenceItemChanged(event.item, event.scopes, event.isChecked)
                    is ButtonClick ->
                        Action.ButtonActions(event.actions)
                    is ContactManagementAddClick ->
                        Action.AddChannel(event.item)
                    is ContactManagementRemoveClick ->
                        Action.RemoveChannel(event.item, event.channel)
                }
            }
            .onEach { action -> viewModel.handle(action) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        views.errorRetryButton.setOnClickListener { viewModel.handle(Action.Refresh) }
    }

    override fun onResume() {
        super.onResume()

        viewModel.handle(Action.Refresh)
    }

    /**
     * Sets the [OnDisplayPreferenceCenterListener].
     */
    fun setOnDisplayPreferenceCenterListener(listener: OnDisplayPreferenceCenterListener?) {
        onDisplayListener = listener
    }

    /**
     * Shows the title and description as an item at the top of the list.
     */
    fun showHeaderItem(title: String?, description: String?) {
        adapter.setHeaderItem(title, description)
    }

    private fun render(state: State): Unit = when (state) {
        is State.Loading -> {
            views.showLoading()
            adapter.submit(
                items = emptyList(),
                channelSubscriptions = emptySet(),
                contactSubscriptions = emptyMap(),
                contactChannels = emptySet()
            )
        }
        is State.Error -> views.showError()
        is State.Content -> {
            onDisplayListener?.let {
                if (!it.onDisplayPreferenceCenter(state.title, state.subtitle)) {
                    showHeaderItem(state.title, state.subtitle)
                }
            } ?: showHeaderItem(state.title, state.subtitle)

            adapter.submit(
                items = state.listItems,
                channelSubscriptions = state.channelSubscriptions,
                contactSubscriptions = state.contactSubscriptions,
                contactChannels = state.contactChannels
            )

            views.showContent()
        }
    }

    private fun handle(effect: Effect) {
        when (effect) {
            is Effect.ShowContactManagementAddDialog ->
                showContactManagementAddDialog(effect.item, viewModel::handle)
            is Effect.ShowContactManagementAddConfirmDialog ->
                effect.item.addPrompt.prompt.onSuccess?.let {
                    showContactManagementAddConfirmDialog(it, effect.result, viewModel::handle)
                }
            is Effect.ShowContactManagementRemoveDialog ->
                showContactManagementRemoveDialog(effect.item, effect.channel, viewModel::handle)
        }
    }
}
