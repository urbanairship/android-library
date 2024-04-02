package com.urbanairship.automation.rewrite.utils

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class ScheduleConditionsChangedNotifier {
    private val waitingList = mutableListOf<Continuation<Unit>>()
    private val lock = Object()

    fun notifyChanged() {
        synchronized(lock) { waitingList.forEach { it.resume(Unit) } }
    }

    suspend fun wait() {
        suspendCoroutine<Unit> {
            synchronized(lock) { waitingList.add(it) }
        }
    }
}
