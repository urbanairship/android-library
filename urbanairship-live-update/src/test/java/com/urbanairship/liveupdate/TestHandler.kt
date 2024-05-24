package com.urbanairship.liveupdate

import android.content.Context

internal class TestHandler : SuspendLiveUpdateCustomHandler {
    private val _events = mutableListOf<Event>()
    internal val events: List<Event>
        get() = _events

    override suspend fun onUpdate(
        context: Context,
        event: LiveUpdateEvent,
        update: LiveUpdate
    ): LiveUpdateResult<Nothing> {
        _events.add(Event(event, update))

        return LiveUpdateResult.ok()
    }

    data class Event(
        val action: LiveUpdateEvent,
        val update: LiveUpdate
    )
}
