/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.json.JsonMatcher
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf

/**
 * Utils for automation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object VersionUtils {

    public const val AMAZON_VERSION_KEY: String = "amazon"
    public const val ANDROID_VERSION_KEY: String = "android"
    public const val VERSION_KEY: String = "version"

    private const val IVY_PATTERN_GREATER_THAN = "]%s,)"
    private const val IVY_PATTERN_GREATER_THAN_OR_EQUAL_TO = "[%s,)"

    private fun getPlatformName(platform: Platform): String {
        return when(platform) {
            Platform.AMAZON -> AMAZON_VERSION_KEY
            else -> ANDROID_VERSION_KEY
        }
    }

    /**
     * Generates the version object.
     *
     * @param platform The platform.
     * @param appVersion The app version.
     * @return The version object.
     */
    public fun createVersionObject(
        platform: Platform,
        appVersion: Long
    ): JsonSerializable {
        // Get the version code
        return jsonMapOf(getPlatformName(platform) to jsonMapOf(VERSION_KEY to appVersion)).toJsonValue()
    }

    /**
     * Creates the version predicate.
     * @param platform The platform.
     * @param versionMatcher The value matcher.
     * @return The version predicate.
     */
    public fun createVersionPredicate(
        platform: Platform,
        versionMatcher: ValueMatcher
    ): JsonPredicate {
        return JsonPredicate
            .newBuilder()
            .addMatcher(
                matcher = JsonMatcher.newBuilder()
                    .setScope(getPlatformName(platform))
                    .setKey(VERSION_KEY)
                    .setValueMatcher(versionMatcher)
                    .build()
            )
            .build()
    }

    /**
     * Returns `true` if the v1 version is newer than the v2 version.
     * If either version is invalid, returns `false`.
     */
    public fun isVersionNewer(v1: String, v2: String): Boolean {
        return try {
            IvyVersionMatcher
                .newMatcher(String.format(IVY_PATTERN_GREATER_THAN, v1))
                .apply(v2)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns `true` if the v1 version is newer than or equal to the v2 version.
     * If either version is invalid, returns `false`.
     */
    public fun isVersionNewerOrEqualTo(v1: String, v2: String): Boolean {
        return try {
            IvyVersionMatcher
                .newMatcher(String.format(IVY_PATTERN_GREATER_THAN_OR_EQUAL_TO, v1))
                .apply(v2)
        } catch (e: Exception) {
            false
        }
    }
}
