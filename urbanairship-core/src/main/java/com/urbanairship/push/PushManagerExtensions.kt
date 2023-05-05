/* Copyright Airship and Contributors */
@file:JvmName("-PushManagerExtensions")
package com.urbanairship.push

import kotlinx.coroutines.flow.StateFlow

/**
 * Airship notification status state flow.
 */
public val PushManager.pushNotificationStatusFlow: StateFlow<PushNotificationStatus>
get() {
    return this.statusObserver.pushNotificationStatusFlow
}
