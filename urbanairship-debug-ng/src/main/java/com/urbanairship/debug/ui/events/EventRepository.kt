/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import androidx.annotation.RestrictTo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Event repository.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class EventRepository(val dao: EventDao) {
    fun getEvents(): MutableStateFlow<List<EventEntity>> = MutableStateFlow(dao.getEvents())
    fun getEvents(types: List<String>): MutableStateFlow<List<EventEntity>> = MutableStateFlow(dao.getEvents(types))
    fun getEvent(eventId: String) = dao.getEvent(eventId)

    fun trimOldEvents(days: Int) {
        dao.trimOldEvents(days)
    }
}
