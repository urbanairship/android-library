package com.urbanairship.liveupdate

import android.content.Context
import androidx.core.app.NotificationCompat

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

/**
 * A notification-backed handler, for tests that depend on the distinction between
 * [NotificationLiveUpdateHandler] and [CustomLiveUpdateHandler] — such as the staleness reaper,
 * which only considers the former.
 */
internal class TestNotificationHandler : SuspendLiveUpdateNotificationHandler() {
    private val _events = mutableListOf<TestHandler.Event>()
    internal val events: List<TestHandler.Event>
        get() = _events

    override suspend fun onUpdate(
        context: Context,
        event: LiveUpdateEvent,
        update: LiveUpdate
    ): LiveUpdateResult<NotificationCompat.Builder> {
        _events.add(TestHandler.Event(event, update))

        return LiveUpdateResult.ok(NotificationCompat.Builder(context, "test-channel"))
    }
}

/** A notification handler that always cancels, as handlers typically do on `END`. */
internal class CancellingNotificationHandler : SuspendLiveUpdateNotificationHandler() {
    override suspend fun onUpdate(
        context: Context,
        event: LiveUpdateEvent,
        update: LiveUpdate
    ): LiveUpdateResult<NotificationCompat.Builder> = LiveUpdateResult.cancel()
}
