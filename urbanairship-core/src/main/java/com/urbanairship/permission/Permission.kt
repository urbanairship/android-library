/* Copyright Airship and Contributors */
package com.urbanairship.permission

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Device permissions.
 *
 * Only [DISPLAY_NOTIFICATIONS] has a built-in delegate. Requesting any other permission
 * requires the app to register a [PermissionDelegate] with [PermissionsManager], otherwise
 * the request resolves to [PermissionStatus.NOT_DETERMINED] and nothing is shown.
 */
public enum class Permission(public val value: String) : JsonSerializable {

    // Display notifications
    DISPLAY_NOTIFICATIONS("display_notifications"),

    // Access location
    LOCATION("location"),

    // App Tracking Transparency. iOS only; Android has no equivalent OS prompt, so this
    // resolves to NOT_DETERMINED unless the app maps it to a consent flow of its own.
    APP_TRACKING_TRANSPARENCY("app_tracking_transparency"),

    // Camera
    CAMERA("camera"),

    // Microphone
    MICROPHONE("microphone"),

    // Bluetooth
    BLUETOOTH("bluetooth"),

    // Photo library
    PHOTO_LIBRARY("photo_library"),

    // Contacts
    CONTACTS("contacts");

    override fun toString(): String = name.lowercase()

    override fun toJsonValue(): JsonValue = JsonValue.wrapOpt(value)

    public companion object {

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): Permission {
            val content = value.requireString().lowercase()
            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Invalid permission: $value")
        }
    }
}
