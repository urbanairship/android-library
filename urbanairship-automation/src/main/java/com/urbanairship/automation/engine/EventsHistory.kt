package com.urbanairship.automation.engine

import com.urbanairship.util.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class EventsHistory(
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {

    companion object {
        internal val HISTORY_DURATION = 30.seconds
        internal const val MAX_EVENTS_COUNT = 100
    }

    private class Entry(
        val timestamp: Long,
        val event: AutomationEvent
    )

    private val history = MutableStateFlow<List<Entry>>(emptyList())

    fun add(event: AutomationEvent) {
        history.update {
            pruneExpired(it) + Entry(clock.currentTimeMillis(), event)
        }
    }

    fun getEvents(): List<AutomationEvent> {
        return pruneExpired(history.value).map { it.event }
    }

    private fun pruneExpired(input: List<Entry>): List<Entry> {
        return input
            .takeLast(MAX_EVENTS_COUNT)
            .filter {
            val diff = (clock.currentTimeMillis() - it.timestamp).milliseconds
            diff < HISTORY_DURATION
        }
    }
}
