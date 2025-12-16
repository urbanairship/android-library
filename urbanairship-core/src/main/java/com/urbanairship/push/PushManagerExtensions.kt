/* Copyright Airship and Contributors */
@file:JvmName("-PushManagerExtensions")
package com.urbanairship.push

import com.urbanairship.permission.PermissionPromptFallback
import com.urbanairship.permission.PermissionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Airship notification status state flow.
 */
@Deprecated("Use pushNotificationStatusFlow property on PushManager instead", ReplaceWith("pushNotificationStatusFlow"))
public val PushManager.pushNotificationStatusFlow: StateFlow<PushNotificationStatus>
get() {
    return this.statusObserver.pushNotificationStatusFlow
}

/**
 * Enables user notifications on Airship and tries to prompt for the notification permission.
 *
 * @note This does NOT enable the [com.urbanairship.PrivacyManager.Feature.PUSH] feature.
 *
 * @param promptFallback Prompt fallback if the the notification permission is silently denied.
 * @return `true` if the notifications are enabled, otherwise `false.
 */
public suspend fun PushManager.enableUserNotifications(promptFallback: PermissionPromptFallback = PermissionPromptFallback.None): Boolean {
    preferenceDataStore.put(PushManager.USER_NOTIFICATIONS_ENABLED_KEY, true)
    val result = permissionsManager.requestPermission(
        permission = Permission.DISPLAY_NOTIFICATIONS,
        fallback = promptFallback
    )
    updateStatusObserver()

    return result.permissionStatus == PermissionStatus.GRANTED
}
