/* Copyright Airship and Contributors */

package com.urbanairship.meteredusage

import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField

internal data class Config(
    val isEnabled: Boolean,
    val initialDelayMs: Long, // milliseconds
    val intervalMs: Long // milliseconds
) {
    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INITIAL_DELAY = "initial_delay_ms"
        private const val KEY_INTERVAL = "interval_ms"

        private const val DEFAULT_INITIAL_DELAY = 15L
        private const val DEFAULT_INTERVAL = 30L

        fun fromJson(json: JsonMap): Config {
            return Config(
                isEnabled = json.optionalField(KEY_ENABLED) ?: false,
                initialDelayMs = json.optionalField(KEY_INITIAL_DELAY) ?: DEFAULT_INITIAL_DELAY,
                intervalMs = json.optionalField(KEY_INTERVAL) ?: DEFAULT_INTERVAL
            )
        }

        fun default() = Config(false, DEFAULT_INITIAL_DELAY, DEFAULT_INTERVAL)
    }
}
