package com.urbanairship.preferencecenter.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.testing.OpenForTesting
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ButtonClick
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ChannelSubscriptionChange
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactSubscriptionChange
import com.urbanairship.preferencecenter.ui.PreferenceCenterAdapter.ItemEvent.ContactSubscriptionGroupChange
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Action
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.PreferenceCenterViewModelFactory
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
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
    @Suppress("ProtectedInFinal")
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
    @Suppress("ProtectedInFinal")
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
            list.setHasFixedSize(true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.states.collect(::render)
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
        is State.Loading -> views.showLoading()
        is State.Error -> views.showError()
        is State.Content -> {
            onDisplayListener?.let {
                if (!it.onDisplayPreferenceCenter(state.title, state.subtitle)) {
                    showHeaderItem(state.title, state.subtitle)
                }
            } ?: showHeaderItem(state.title, state.subtitle)

            adapter.submit(state.listItems, state.channelSubscriptions, state.contactSubscriptions)

            views.showContent()
        }
    }
}

private class SectionDividerDecoration(
    context: Context,
    private val isAnimating: () -> Boolean
) : RecyclerView.ItemDecoration() {
    private val drawable = run {
        val dividerAttr = TypedValue()
        context.theme.resolveAttribute(R.attr.dividerHorizontal, dividerAttr, true)
        ContextCompat.getDrawable(context, dividerAttr.resourceId)
            ?: throw Resources.NotFoundException("Failed to resolve attr 'dividerHorizontal' from theme!")
    }

    private val unlabeledSectionPadding = context.resources.getDimensionPixelSize(R.dimen.ua_preference_center_unlabeled_section_item_top_padding)

    private val dividerHeight: Int = drawable.intrinsicHeight

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (isAnimating()) return

        if (shouldDrawDividerBelow(view, parent)) {
            outRect.bottom = dividerHeight
        } else if (isSectionWithoutLabeledBreak(view, parent)) {
            outRect.top = unlabeledSectionPadding
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (isAnimating()) return

        val width = parent.width
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (shouldDrawDividerBelow(child, parent)) {
                val top = (child.y + child.height).toInt()
                drawable.setBounds(0, top, width, top + dividerHeight)
                drawable.draw(c)
            }
        }
    }

    private fun shouldDrawDividerBelow(view: View, parent: RecyclerView): Boolean {
        val holder = parent.getChildViewHolder(view)
        val isNotSectionItem = holder !is PrefCenterItem.SectionItem.ViewHolder &&
            holder !is PrefCenterItem.SectionBreakItem.ViewHolder

        val index = parent.indexOfChild(view)
        return if (index < parent.childCount - 1) {
            val nextView = parent.getChildAt(index + 1)
            val nextHolder = parent.getChildViewHolder(nextView)
            val isNextSectionItem = nextHolder is PrefCenterItem.SectionItem.ViewHolder ||
                nextHolder is PrefCenterItem.SectionBreakItem.ViewHolder
            val isNextAlert = nextHolder is PrefCenterItem.AlertItem.ViewHolder

            isNotSectionItem && isNextSectionItem || isNextAlert
        } else {
            false
        }
    }

    private fun isSectionWithoutLabeledBreak(view: View, parent: RecyclerView): Boolean {
        val holder = parent.getChildViewHolder(view)
        val isSectionItem = holder is PrefCenterItem.SectionItem.ViewHolder

        val index = parent.indexOfChild(view)
        return if (index < parent.childCount && index > 0) {
            val prevView = parent.getChildAt(index - 1)
            val prevHolder = parent.getChildViewHolder(prevView)
            val isPrevSectionBreak = prevHolder is PrefCenterItem.SectionBreakItem.ViewHolder

            isSectionItem && !isPrevSectionBreak
        } else {
            false
        }
    }
}
