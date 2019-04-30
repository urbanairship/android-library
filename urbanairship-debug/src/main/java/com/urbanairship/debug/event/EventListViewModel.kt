/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.Transformations
import android.arch.lifecycle.ViewModel
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import android.databinding.Observable
import android.support.annotation.RestrictTo
import com.urbanairship.debug.event.persistence.EventEntity

/**
 * Event list view model.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class EventListViewModel(repository: EventRepository) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 30
    }

    val filters: List<EventFilter> = EventInfo.KNOWN_TYPES.map { EventFilter(it) }
    val events = MediatorLiveData<PagedList<EventEntity>>()
    val activeFiltersLiveData = MediatorLiveData<List<EventFilter>>()

    private var activeFilters = ArrayList<EventFilter>()

    init {
        val pageListConfig = PagedList.Config.Builder()
                .setPageSize(PAGE_SIZE)
                .setEnablePlaceholders(false)
                .build()


        filters.forEach {
            it.isChecked.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
                override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                    if (it.isChecked.get()) {
                        if (!activeFilters.contains(it)) {
                            activeFilters.add(it)
                            activeFiltersLiveData.value = activeFilters
                        }
                    } else {
                        if (activeFilters.contains(it)) {
                            activeFilters.remove(it)
                            activeFiltersLiveData.value = activeFilters
                        }
                    }
                }
            })
        }

        activeFiltersLiveData.value = activeFilters

        val filteredEvents = Transformations.switchMap(activeFiltersLiveData) {
            if (it.isEmpty()) {
                LivePagedListBuilder(repository.getEvents(), pageListConfig).build()
            } else {
                val types = it.filter { it.isChecked.get() }.map { it.type }.toList()
                LivePagedListBuilder(repository.getEvents(types), pageListConfig).build()
            }
        }

        events.addSource(filteredEvents, events::setValue)
    }

    fun clearFilters() {
        activeFilters.clear()
        activeFiltersLiveData.value = activeFilters

        filters.forEach {
            it.isChecked.set(false)
        }
    }
}
