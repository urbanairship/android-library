/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.event

import android.arch.paging.DataSource
import android.support.annotation.RestrictTo
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
}
