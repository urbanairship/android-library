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
     * If notifications are allowed at the system level for the application.
     */
    public val areNotificationsAllowed: Boolean,

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
     * Checks if [isUserNotificationsEnabled], [areNotificationsAllowed], and [isPushPrivacyFeatureEnabled]
     * is enabled.
     */
    public val isUserOptedIn: Boolean
    get() {
        return isUserNotificationsEnabled && areNotificationsAllowed && isPushPrivacyFeatureEnabled
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
            areNotificationsAllowed,
            isPushPrivacyFeatureEnabled,
            isPushTokenRegistered
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PushNotificationStatus

        if (isUserNotificationsEnabled != other.isUserNotificationsEnabled) return false
        if (areNotificationsAllowed != other.areNotificationsAllowed) return false
        if (isPushPrivacyFeatureEnabled != other.isPushPrivacyFeatureEnabled) return false
        if (isPushTokenRegistered != other.isPushTokenRegistered) return false

        return true
    }

    override fun toString(): String {
        return "PushNotificationStatus(isUserNotificationsEnabled=$isUserNotificationsEnabled, " +
                "isPushPermissionGranted=$areNotificationsAllowed, " +
                "isPushPrivacyFeatureEnabled=$isPushPrivacyFeatureEnabled, " +
                "isPushTokenRegistered=$isPushTokenRegistered)"
    }
}
