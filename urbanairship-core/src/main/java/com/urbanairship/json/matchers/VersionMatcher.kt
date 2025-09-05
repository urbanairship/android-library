/* Copyright Airship and Contributors */
package com.urbanairship.json.matchers

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

    override fun toJsonValue(): JsonValue = jsonMapOf(VERSION_KEY to versionMatcher).toJsonValue()

    override fun apply(value: JsonValue, ignoreCase: Boolean): Boolean {
        return value.string?.let { versionMatcher.apply(it) } == true
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as VersionMatcher

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
