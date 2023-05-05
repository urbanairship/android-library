/* Copyright Airship and Contributors */

package com.urbanairship.push

import androidx.core.util.ObjectsCompat

/**
 * A snapshot of the Airship push notification status.
 */
public class PushNotificationStatus(
    /**
     * If user notifications are enabled on [PushManager].
     */
    public val isUserNotificationsEnabled: Boolean,

    /**
     * If the permission to display a notification is granted.
     */
    public val isDisplayNotificationsPermissionGranted: Boolean,

    /**
     * If the [PrivacyManager#FEATURE_PUSH] is enabled.
     */
    public val isPushPrivacyFeatureEnabled: Boolean,

    /**
     * If push registration was able to generate a token.
     */
    public val isPushTokenRegistered: Boolean,
) {

    /**
     * Checks if [isUserNotificationsEnabled], [isDisplayNotificationsPermissionGranted], and [isPushPrivacyFeatureEnabled]
     * is enabled.
     */
    public val isUserOptedIn: Boolean
    get() {
        return isUserNotificationsEnabled && isDisplayNotificationsPermissionGranted && isPushPrivacyFeatureEnabled
    }

    /**
     * Checks if [isUserOptedIn] and [isPushTokenRegistered] is enabled.
     */
    public val isOptIn: Boolean
        get() {
            return isUserOptedIn && isPushTokenRegistered
        }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(
            isUserNotificationsEnabled,
            isDisplayNotificationsPermissionGranted,
            isPushPrivacyFeatureEnabled,
            isPushTokenRegistered
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PushNotificationStatus

        if (isUserNotificationsEnabled != other.isUserNotificationsEnabled) return false
        if (isDisplayNotificationsPermissionGranted != other.isDisplayNotificationsPermissionGranted) return false
        if (isPushPrivacyFeatureEnabled != other.isPushPrivacyFeatureEnabled) return false
        if (isPushTokenRegistered != other.isPushTokenRegistered) return false

        return true
    }

    override fun toString(): String {
        return "PushNotificationStatus(isUserNotificationsEnabled=$isUserNotificationsEnabled, " +
                "isPushPermissionGranted=$isDisplayNotificationsPermissionGranted, " +
                "isPushPrivacyFeatureEnabled=$isPushPrivacyFeatureEnabled, " +
                "isPushTokenRegistered=$isPushTokenRegistered)"
    }
}
