package com.urbanairship

import com.urbanairship.http.RequestException

/**
 * Represents the mobile platforms supported by Airship.
 */
public enum class Platform(internal val rawValue: Int) {
    /**
     * Amazon platform. For devices using Amazon Device Messaging (ADM).
     */
    AMAZON(1),

    /**
     * Android platform. For devices using Firebase Cloud Messaging (FCM) or Huawei Mobile Services (HMS).
     */
    ANDROID(2),

    /**
     * Platform cannot be determined. This may occur if all features have been disabled in the [PrivacyManager].
     */
    UNKNOWN(-1);

    /**
     * The string value of the platform.
     */
    public val stringValue: String
        get() {
            return when(this) {
                AMAZON -> "amazon"
                ANDROID -> "android"
                UNKNOWN -> "unknown"
            }
        }

    internal val deviceType: String
        get() {
            when(this) {
                UNKNOWN -> throw RequestException("Invalid platform")
                else -> return this.stringValue
            }
        }

    internal companion object {
        fun fromRawValue(rawValue: Int): Platform {
            return entries.find { it.rawValue == rawValue } ?: UNKNOWN
        }
    }
}
