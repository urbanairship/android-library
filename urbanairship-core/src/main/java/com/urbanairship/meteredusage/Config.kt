/* Copyright Airship and Contributors */

package com.urbanairship.meteredusage

import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField

internal data class Config(
    val isEnabled: Boolean,
    val initialDelay: Long, // milliseconds
    val interval: Long // milliseconds
) {
    companion object {
        private const val KEY_ENABLED = "isEnabled"
        private const val KEY_INITIAL_DELAY = "initialDelay"
        private const val KEY_INTERVAL = "interval"

        private const val DEFAULT_INITIAL_DELAY = 15L
        private const val DEFAULT_INTERVAL = 30L

        fun fromJson(json: JsonMap): Config {
            return Config(
                isEnabled = json.optionalField(KEY_ENABLED) ?: false,
                initialDelay = json.optionalField(KEY_INITIAL_DELAY) ?: DEFAULT_INITIAL_DELAY,
                interval = json.optionalField(KEY_INTERVAL) ?: DEFAULT_INTERVAL
            )
        }

        fun default() = Config(false, DEFAULT_INITIAL_DELAY, DEFAULT_INTERVAL)
    }
}
