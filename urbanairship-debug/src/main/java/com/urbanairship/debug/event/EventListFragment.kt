/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableFloat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.urbanairship.debug.R
import com.urbanairship.debug.ServiceLocator
import com.urbanairship.debug.databinding.UaFragmentEventListBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Event list fragment.
 */
class EventListFragment : Fragment() {

    companion object {
        // Threshold for when we start fading in the collapse view with the expand view
        private const val COLLAPSE_CHANGEOVER_THRESHOLD = 0.4f
        // Threshold for when we reach max alpha value for the collapse view
        private const val COLLAPSE_MAX_THRESHOLD = 0.67f
        // Threshold for when we reach max alpha value for the expand view
        private const val EXPAND_MAX_THRESHOLD = 0f
        // Preference data store key for the number of days of events to keep in the datastore
        const val STORAGE_DAYS_KEY = "com.urbanairship.debug.event.STORAGE_DAYS"
        // Default number of days of events to keep in the datastore
        const val DEFAULT_STORAGE_DAYS = 30
    }

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProviders.of(this, ViewModelFactory(context!!)).get(EventListViewModel::class.java)
    }

    private var collapseAlpha = ObservableFloat(0f)
    private var expandAlpha = ObservableFloat(0f)
    private var isFilterSheetVisible = ObservableBoolean(false)

    private val areFiltersActive: Boolean
        get() {
            viewModel.activeFiltersLiveData.value?.let {
                return it.isNotEmpty()
            }
            return false
        }

    private var sharedPreferences: SharedPreferences? = null

    private var storageDays: Int
        get() {
            sharedPreferences?.let {
                return it.getInt(STORAGE_DAYS_KEY, DEFAULT_STORAGE_DAYS)
            }
            return DEFAULT_STORAGE_DAYS
        }
        set(days) {
            // save to shared preferences
            sharedPreferences?.apply {
                edit().putInt(EventListFragment.STORAGE_DAYS_KEY, days).apply()
            }
        }

    override fun onAttach(context: Context) {
        context.theme.applyStyle(com.google.android.material.R.style.Theme_MaterialComponents, false)
        sharedPreferences = ServiceLocator.shared(context).sharedPreferences
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val dataBinding = DataBindingUtil.inflate<UaFragmentEventListBinding>(inflater, R.layout.ua_fragment_event_list, container, false)
        bottomSheetBehavior = BottomSheetBehavior.from(dataBinding.filterSheet)
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(p0: View, slideOffset: Float) {
                updateCollapseAlpha(slideOffset)
            }

            override fun onStateChanged(p0: View, state: Int) {
                updateFiltersLayout()
            }
        })

        val filterAdapter = EventFilterAdapter()
        filterAdapter.submitList(viewModel.filters)

        val eventAdapter = EventAdapter {
            if (isResumed) {
                val args = Bundle()
                args.putString(EventDetailsFragment.ARGUMENT_EVENT_ID, it.eventId)
                Navigation.findNavController(dataBinding.root).navigate(R.id.eventDetailsFragment, args)
            }
        }

        viewModel.events.observe(this, Observer(eventAdapter::submitList))
        viewModel.activeFiltersLiveData.observe(this, Observer {
            updateFiltersLayout()
        })

        dataBinding.apply {
            lifecycleOwner = this@EventListFragment
            viewModel = this@EventListFragment.viewModel
            collapseAlpha = this@EventListFragment.collapseAlpha
            expandAlpha = this@EventListFragment.expandAlpha
            isFilterSheetVisible = this@EventListFragment.isFilterSheetVisible

            eventFilters.apply {
                adapter = filterAdapter
            }

            events.apply {
                addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(context, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL))
                adapter = eventAdapter
            }

            collapse.setOnClickListener {
                if (areFiltersActive) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                } else {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }

            expand.setOnClickListener {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }

            fab.setOnClickListener {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        if (savedInstanceState == null) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            updateFiltersLayout()
        }

        return dataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbarWithNavController(R.id.toolbar)?.let {
            // inflate the menu
            it.inflateMenu(R.menu.ua_menu_event)

            // initialize radio buttons
            var radioBoxItem : MenuItem? = null
            when (storageDays) {
                2 -> {
                    radioBoxItem = it.menu.findItem(R.id.ua_event_storage_days_02)
                }
                5 -> {
                    radioBoxItem = it.menu.findItem(R.id.ua_event_storage_days_05)
                }
                10 -> {
                    radioBoxItem = it.menu.findItem(R.id.ua_event_storage_days_10)
                }
                30 -> {
                    radioBoxItem = it.menu.findItem(R.id.ua_event_storage_days_30)
                }
            }

            radioBoxItem?.let {
                it.isChecked = true
            }

            // set up click handlers
            it.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.ua_event_settings -> {
                    }
                    else -> {
                        if (it.groupId == R.id.ua_storage_days) {
                            when (it.itemId) {
                                R.id.ua_event_storage_days_02 -> {
                                    storageDays = 2;
                                }
                                R.id.ua_event_storage_days_05 -> {
                                    storageDays = 5;
                                }
                                R.id.ua_event_storage_days_10 -> {
                                    storageDays = 10;
                                }
                                R.id.ua_event_storage_days_30 -> {
                                    storageDays = 30;
                                }
                             }
                            GlobalScope.launch(Dispatchers.IO) {
                                ServiceLocator.shared(context!!)
                                        .getEventRepository()
                                        .trimOldEvents(storageDays)
                            }
                            if (it.isCheckable) {
                                it.isChecked = true
                            }
                        }
                    }
                }
                false
            }
        }
    }

    private fun updateCollapseAlpha(slideOffset: Float) {
        collapseAlpha.set(offsetToAlpha(slideOffset, COLLAPSE_CHANGEOVER_THRESHOLD, COLLAPSE_MAX_THRESHOLD))
        expandAlpha.set(offsetToAlpha(slideOffset, COLLAPSE_CHANGEOVER_THRESHOLD, EXPAND_MAX_THRESHOLD))
    }

    /**
     * Map a slideOffset (in the range `[-1, 1]`) to an alpha value based on the desired range.
     * For example, `offsetToAlpha(0.5, 0.25, 1) = 0.33` because 0.5 is 1/3 of the way between 0.25
     * and 1. The result value is additionally clamped to the range `[0, 1]`.
     *
     * Taken from https://github.com/google/iosched/master/mobile/src/nav_home/java/com/google/samples/apps/iosched/ui/schedule/filters/ScheduleFilterFragment.kt
     */
    private fun offsetToAlpha(value: Float, rangeMin: Float, rangeMax: Float): Float {
        return ((value - rangeMin) / (rangeMax - rangeMin)).coerceIn(0f, 1f)
    }

    internal class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EventListViewModel(ServiceLocator.shared(context).getEventRepository()) as T
        }
    }

    fun updateFiltersLayout() {
        val state = bottomSheetBehavior.state
        val isHideable = !areFiltersActive

        bottomSheetBehavior.isHideable = isHideable
        bottomSheetBehavior.skipCollapsed = isHideable

        if (state == BottomSheetBehavior.STATE_COLLAPSED && isHideable) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        val isFilterSheetVisible = bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
        if (isFilterSheetVisible != this.isFilterSheetVisible.get()) {
            this.isFilterSheetVisible.set(isFilterSheetVisible)
        }
    }
}
