package com.urbanairship.preferencecenter

import com.urbanairship.UAirship
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.push.PushManager
import com.urbanairship.push.pushNotificationStatusFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class ConditionStateMonitor(
    private val pushManager: PushManager = UAirship.shared().pushManager,
) {

    val states = pushManager.pushNotificationStatusFlow
        .map {
            Condition.State(isOptedIn = it.isUserOptedIn)
        }
        .distinctUntilChanged()

    val currentState
        get() = Condition.State(isOptedIn = isOptedIn)

    private val isOptedIn: Boolean
        get() = pushManager.pushNotificationStatus.isUserOptedIn
}
