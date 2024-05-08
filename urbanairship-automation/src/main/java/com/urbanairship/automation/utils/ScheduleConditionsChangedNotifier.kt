package com.urbanairship.automation.utils

import androidx.annotation.RestrictTo
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ScheduleConditionsChangedNotifier {
    private val waitingList = mutableListOf<Continuation<Unit>>()

    internal fun notifyChanged() {
        synchronized(waitingList) {
            waitingList.forEach { it.resume(Unit) }
            waitingList.clear()
        }
    }

    internal suspend fun wait() {
        suspendCoroutine<Unit> {
            synchronized(waitingList) { waitingList.add(it) }
        }
    }
}
