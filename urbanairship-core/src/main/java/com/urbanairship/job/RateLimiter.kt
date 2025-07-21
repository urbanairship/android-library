/* Copyright Airship and Contributors */
package com.urbanairship.job

import androidx.annotation.IntRange
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.util.Clock
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Rate limit tracker.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RateLimiter(
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {

    private val hits = MutableStateFlow<Map<String, List<Long>>>(emptyMap())
    private val rules = MutableStateFlow<Map<String, Rule>>(emptyMap())
    private val lock = Any()

    /**
     * Tracks a rate limit.
     *
     * @param limitId The limit Id.
     */
    public fun track(limitId: String) {
        synchronized(lock) {
            val recordedHits = hits.value[limitId] ?: return
            val limiterRule = rules.value[limitId] ?: return
            val currentTime = clock.currentTimeMillis()

            this.hits.update { current ->
                current
                    .toMutableMap()
                    .apply { put(limitId, filter(recordedHits + currentTime, limiterRule, currentTime)) }
                    .toMap()
            }
        }
    }

    /**
     * Gets the rate limit status.
     *
     * @param limitId The limit Id.
     * @return The status if a limit exists, otherwise `null`.
     */
    public fun status(limitId: String): Status? {
        synchronized(lock) {
            val recordedHits = hits.value[limitId] ?: return null
            val appliedRule = rules.value[limitId] ?: return null
            val currentTime = clock.currentTimeMillis()

            val updatedHits = filter(recordedHits, appliedRule, currentTime)
            val result = if (updatedHits.size >= appliedRule.rate) {
                val nextExpired = appliedRule.duration - (currentTime - updatedHits[updatedHits.size - appliedRule.rate]).milliseconds
                Status(LimitStatus.OVER, nextExpired)
            } else {
                Status(LimitStatus.UNDER, 0.seconds)
            }

            hits.update { current ->
                current
                    .toMutableMap()
                    .apply { put(limitId, updatedHits) }
                    .toMap()
            }

            return result
        }
    }

    /**
     * Sets the limit.
     *
     * @param limitId Limit Id.
     * @param rate The number of events for the duration.
     * @param duration The duration.
     */
    public fun setLimit(
        limitId: String,
        @IntRange(from = 1) rate: Int,
        duration: Duration
    ) {
        synchronized(lock) {
            rules.update { current ->
                current
                    .toMutableMap()
                    .apply { put(limitId, Rule(rate, duration)) }
                    .toMap()
            }

            hits.update { current ->
                current
                    .toMutableMap()
                    .apply { put(limitId, emptyList()) }
                    .toMap()
            }
        }
    }

    private fun filter(hits: List<Long>, rule: Rule, currentTimeMs: Long): List<Long> {
        return hits.filter { it + rule.duration.inWholeMilliseconds > currentTimeMs }
    }

    /**
     * Limit status.
     */
    public enum class LimitStatus {

        /**
         * Over the limit.
         */
        OVER,

        /**
         * Under the limit.
         */
        UNDER
    }

    /**
     * Rate limit status.
     */
    public class Status @VisibleForTesting public constructor(
        /**
         * The status.
         *
         * @return The status.
         */
        public val limitStatus: LimitStatus,
        public val nextAvailable: Duration
    )

    private class Rule(val rate: Int, val duration: Duration)
}
