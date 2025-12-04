/* Copyright Airship and Contributors */
package com.urbanairship.json.matchers

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.IvyVersionMatcher

/**
 * Version matcher.
 *
 * @hide
 */
internal class VersionMatcher (
    private val versionMatcher: IvyVersionMatcher
) : ValueMatcher() {

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(VERSION_KEY to versionMatcher).toJsonValue()

    override fun apply(jsonValue: JsonValue, ignoreCase: Boolean): Boolean {
        return jsonValue.string?.let { versionMatcher.apply(it) } == true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as VersionMatcher

        return versionMatcher == that.versionMatcher
    }

    override fun hashCode(): Int {
        return versionMatcher.hashCode()
    }

    companion object {
        const val VERSION_KEY: String = "version_matches"
        const val ALTERNATE_VERSION_KEY: String = "version"
    }
}
