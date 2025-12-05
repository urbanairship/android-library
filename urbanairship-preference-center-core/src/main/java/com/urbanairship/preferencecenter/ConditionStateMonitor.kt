package com.urbanairship.preferencecenter

import androidx.annotation.RestrictTo
import com.urbanairship.Airship
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.push.PushManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ConditionStateMonitor(
    private val pushManager: PushManager = Airship.push,
) {

    public val states: Flow<Condition.State> = pushManager.pushNotificationStatusFlow
        .map {
            Condition.State(isOptedIn = it.isUserOptedIn)
        }
        .distinctUntilChanged()

    public val currentState: Condition.State
        get() = Condition.State(isOptedIn = isOptedIn)

    private val isOptedIn: Boolean
        get() = pushManager.pushNotificationStatus.isUserOptedIn
}
