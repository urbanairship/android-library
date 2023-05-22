package com.urbanairship.liveupdate

import android.content.Context

internal class TestHandler : LiveUpdateCustomHandler {
    private val _events = mutableListOf<Event>()
    internal val events: List<Event>
        get() = _events.toList()

    override fun onUpdate(context: Context, event: LiveUpdateEvent, update: LiveUpdate): LiveUpdateResult<Nothing> {
        println("HANDLER onUpdate(action: $event, update: $update")
        _events.add(Event(event, update))

        return LiveUpdateResult.ok()
    }

    data class Event(
        val action: LiveUpdateEvent,
        val update: LiveUpdate
    )
}
