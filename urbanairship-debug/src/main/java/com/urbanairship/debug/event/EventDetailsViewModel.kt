/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import androidx.annotation.RestrictTo
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.urbanairship.debug.event.persistence.EventEntity
import org.json.JSONObject

/**
 * View model for event details.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class EventDetailsViewModel(repository: EventRepository, eventId: String) : ViewModel() {

    private val eventEntity: LiveData<EventEntity?> = repository.getEvent(eventId)

    fun eventData(): LiveData<String> {
        return eventEntity.map {
            if (it == null) {
                "event not found"
            } else {
                JSONObject(it.payload).toString(4)
            }
        }
    }
}
