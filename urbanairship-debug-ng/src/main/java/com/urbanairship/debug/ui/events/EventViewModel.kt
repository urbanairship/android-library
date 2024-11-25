/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import androidx.annotation.RestrictTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class EventViewModel(private val repository: EventRepository) : ViewModel() {
    var events: MutableStateFlow<List<EventEntity>> = MutableStateFlow(emptyList())

    init {
        refresh()
    }

    fun refresh() {
        CoroutineScope(Dispatchers.IO).launch {
            events = repository.getEvents()
        }
    }
}

internal class EventViewModelFactory(private val eventRepository: EventRepository) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = EventViewModel(eventRepository) as T
}
