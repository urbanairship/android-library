/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Transformations
import android.arch.lifecycle.ViewModel
import android.support.annotation.RestrictTo
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
        return Transformations.map(eventEntity) {
            if (it == null) {
                "event not found"
            } else {
                JSONObject(it.payload).toString(4)
            }
        }
    }
}
