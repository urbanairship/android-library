/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import androidx.annotation.RestrictTo
import androidx.paging.DataSource
import com.urbanairship.debug.event.persistence.EventDao
import com.urbanairship.debug.event.persistence.EventEntity

/**
 * Event repository.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class EventRepository(val dao: EventDao) {
    fun getEvents(): DataSource.Factory<Int, EventEntity> = dao.getEvents()
    fun getEvents(types: List<String>): DataSource.Factory<Int, EventEntity> = dao.getEvents(types)
    fun getEvent(eventId: String) = dao.getEvent(eventId)

    fun trimOldEvents(days: Int) {
        dao.trimOldEvents(days)
    }
}
