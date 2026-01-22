package com.urbanairship.preferencecenter.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.RestrictTo
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ButtonClick
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ChannelSubscriptionChange
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactManagementAddClick
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactManagementRemoveClick
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactManagementResendClick
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactSubscriptionChange
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactSubscriptionGroupChange
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Action
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Effect
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State
import com.urbanairship.preferencecenter.widget.SectionDividerDecoration
import com.urbanairship.preferencecenter.widget.showContactManagementAddConfirmDialog
import com.urbanairship.preferencecenter.widget.showContactManagementAddDialog
import com.urbanairship.preferencecenter.widget.showContactManagementRemoveDialog
import com.urbanairship.preferencecenter.widget.showContactManagementResentDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * A View that displays a Preference Center.
 *
 * This View provides the same functionality as [PreferenceCenterFragment] but can be used
 * directly in layouts without requiring a Fragment.
 *
 * The [preferenceCenterId] must be set before the View is attached to a window. It can be set
 * either programmatically or via the `app:preferenceCenterId` XML attribute.
 */
public class PreferenceCenterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * Listener to override Preference Center display behavior.
     */
    public fun interface OnDisplayPreferenceCenterListener {

        /**
         * Called when a Preference Center title and description will be displayed.
         *
         * @param title Title of the Preference Center.
         * @param description Description of the Preference Center.
         * @return `true` if the title and description were displayed, otherwise `false` to trigger the default display as an item at the top of the list.
         */
        public fun onDisplayPreferenceCenter(title: String?, description: String?): Boolean
    }

    /**
     * The ID of the Preference Center to be displayed.
     *
     * This must be set before the View is attached to a window.
     */
    public var preferenceCenterId: String? = null
        set(value) {
            val oldValue = field
            field = value
            if (isAttachedToWindow && value != null && value != oldValue) {
                initializeViewModel()
            }
        }

    private var onDisplayListener: OnDisplayPreferenceCenterListener? = null

    private var viewModel: PreferenceCenterViewModel? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var viewModelStoreOwner: ViewModelStoreOwner? = null

    private val viewModelScopeProvider: () -> CoroutineScope = {
        viewModel?.viewModelScope ?: throw IllegalStateException("ViewModel not initialized")
    }

    private val adapter: PreferenceCenterAdapter by lazy {
        PreferenceCenterAdapter(scopeProvider = viewModelScopeProvider)
    }

    private lateinit var views: Views

    private val contactManagementDialogErrors = Channel<String>(Channel.UNLIMITED)
    private val contactManagementDialogDismisses = MutableSharedFlow<Unit>()

    private var statesJob: Job? = null
    private var effectsJob: Job? = null
    private var adapterEventsJob: Job? = null

    private var isInitialized = false

    init {
        // Read XML attributes
        context.theme.obtainStyledAttributes(attrs, R.styleable.UrbanAirship_PreferenceCenterView, 0, 0).apply {
            try {
                preferenceCenterId = getString(R.styleable.UrbanAirship_PreferenceCenterView_preferenceCenterId)
            } finally {
                recycle()
            }
        }

        // Inflate the layout
        val themedContext = ContextThemeWrapper(context, R.style.UrbanAirship_PreferenceCenter_Fragment)
        LayoutInflater.from(themedContext).inflate(R.layout.ua_fragment_preference_center, this, true)

        setupViews()
    }

    private fun setupViews() {
        views = Views(this)

        with(views) {
            list.adapter = adapter
            list.layoutManager = LinearLayoutManager(context)
            list.addItemDecoration(SectionDividerDecoration(context, list::isAnimating))
            list.setHasFixedSize(true)
        }

        views.errorRetryButton.setOnClickListener {
            viewModel?.handle(Action.Refresh)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        lifecycleOwner = findViewTreeLifecycleOwner()
        viewModelStoreOwner = findViewTreeViewModelStoreOwner()

        if (preferenceCenterId != null) {
            initializeViewModel()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        cancelJobs()
        lifecycleOwner = null
        viewModelStoreOwner = null
        isInitialized = false
    }

    private fun cancelJobs() {
        statesJob?.cancel()
        effectsJob?.cancel()
        adapterEventsJob?.cancel()
        statesJob = null
        effectsJob = null
        adapterEventsJob = null
    }

    private fun initializeViewModel() {
        val prefCenterId = preferenceCenterId ?: return
        val owner = lifecycleOwner ?: return
        val vmStoreOwner = viewModelStoreOwner ?: return

        // Cancel any existing jobs before reinitializing
        cancelJobs()

        // Create or retrieve the ViewModel scoped to the ViewModelStoreOwner
        viewModel = ViewModelProvider(
            owner = vmStoreOwner,
            factory = PreferenceCenterViewModel.factory(prefCenterId)
        ).get(
            key = prefCenterId, // Ensure we create a unique VM per pref center ID
            modelClass = PreferenceCenterViewModel::class.java
        )

        val vm = viewModel ?: return

        // Collect states
        statesJob = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.states.collect(::render)
            }
        }

        // Collect effects
        effectsJob = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.effects.collect(::handle)
            }
        }

        // Handle adapter events
        adapterEventsJob = adapter.itemEvents
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
                        Action.RequestAddChannel(event.item)
                    is ContactManagementRemoveClick ->
                        Action.RequestRemoveChannel(event.item, event.channel)
                    is ContactManagementResendClick ->
                        Action.ResendChannelVerification(event.item, event.channel)
                }
            }
            .onEach { action -> vm.handle(action) }
            .launchIn(owner.lifecycleScope)

        // Trigger initial refresh
        if (!isInitialized) {
            vm.handle(Action.Refresh)
            isInitialized = true
        }
    }

    /**
     * Refreshes the Preference Center content.
     *
     * Call this method to force a refresh of the preference center data.
     */
    public fun refresh() {
        viewModel?.handle(Action.Refresh)
    }

    /**
     * Sets the [OnDisplayPreferenceCenterListener].
     */
    public fun setOnDisplayPreferenceCenterListener(listener: OnDisplayPreferenceCenterListener?) {
        onDisplayListener = listener
    }

    /**
     * Shows the title and description as an item at the top of the list.
     */
    public fun showHeaderItem(title: String?, description: String?) {
        adapter.setHeaderItem(title, description)
    }

    private fun render(state: State): Unit = when (state) {
        is State.Loading -> views.showLoading()
        is State.Error -> views.showError()
        is State.Content -> {
            if (onDisplayListener?.onDisplayPreferenceCenter(state.title, state.subtitle) != true) {
                showHeaderItem(state.title, state.subtitle)
            }

            adapter.submit(
                items = state.listItems,
                channelSubscriptions = state.channelSubscriptions,
                contactSubscriptions = state.contactSubscriptions,
                contactChannels = state.contactChannelState
            )

            views.showContent()
        }
    }

    private suspend fun handle(effect: Effect) {
        val owner = lifecycleOwner ?: return
        val vm = viewModel ?: return

        when (effect) {
            is Effect.ShowContactManagementAddDialog ->
                showContactManagementAddDialog(
                    context = context,
                    scope = owner.lifecycleScope,
                    item = effect.item,
                    onHandleAction = vm::handle,
                    errors = contactManagementDialogErrors.consumeAsFlow(),
                    dismisses = contactManagementDialogDismisses
                )
            is Effect.ShowContactManagementAddConfirmDialog ->
                effect.item.addPrompt.prompt.onSubmit?.let { message ->
                    showContactManagementAddConfirmDialog(context, message)
                }
            is Effect.ShowContactManagementRemoveDialog ->
                showContactManagementRemoveDialog(context, effect.item, effect.channel, vm::handle)
            is Effect.ShowChannelVerificationResentDialog ->
                effect.item.platform.resendOptions.onSuccess?.let { message ->
                    showContactManagementResentDialog(context, message)
                }
            Effect.DismissContactManagementAddDialog ->
                contactManagementDialogDismisses.emit(Unit)
            is Effect.ShowContactManagementAddDialogError ->
                contactManagementDialogErrors.send(effect.message)
        }
    }

    private data class Views(
        val view: View,
        val list: RecyclerView = view.findViewById(R.id.list),
        val loading: ViewGroup = view.findViewById(R.id.loading),
        val error: ViewGroup = view.findViewById(R.id.error),
        val errorMessage: TextView = error.findViewById(R.id.error_text),
        val errorRetryButton: Button = error.findViewById(R.id.error_button)
    ) {
        fun showContent() {
            error.isVisible = false
            loading.isVisible = false

            list.isVisible = true
        }

        fun showError() {
            list.isVisible = false
            loading.isVisible = false

            error.isVisible = true
        }

        fun showLoading() {
            list.isVisible = false
            error.isVisible = false

            loading.isVisible = true
        }
    }
}
