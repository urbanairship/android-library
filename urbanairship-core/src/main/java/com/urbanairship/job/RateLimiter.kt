/* Copyright Airship and Contributors */
package com.urbanairship.job

import androidx.annotation.IntRange
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.util.Clock
import com.urbanairship.util.minus
import com.urbanairship.util.plus
import java.time.Instant
import kotlin.time.Duration
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

    private data class RateLimitData(
        val hits: Map<String, List<Instant>> = emptyMap(),
        val rules: Map<String, Rule> = emptyMap()
    ) {
        fun withRule(limitId: String, rule: Rule): RateLimitData {
            return copy(rules = rules + (limitId to rule))
        }

        fun withHits(limitId: String, hits: List<Instant>): RateLimitData {
            return copy(hits = this.hits + (limitId to hits))
        }

        fun withRuleAndResetHits(limitId: String, rule: Rule): RateLimitData {
            return copy(
                rules = rules + (limitId to rule),
                hits = hits + (limitId to emptyList())
            )
        }
    }

    private val data = MutableStateFlow(RateLimitData())

    /**
     * Tracks a rate limit.
     *
     * @param limitId The limit Id.
     */
    public fun track(limitId: String) {
        val currentTime = clock.now()

        data.update { current ->
            val recordedHits = current.hits[limitId] ?: return@update current
            val limiterRule = current.rules[limitId] ?: return@update current

            val updatedHits = filter(recordedHits + currentTime, limiterRule, currentTime)
            current.withHits(limitId, updatedHits)
        }
    }

    /**
     * Gets the rate limit status.
     *
     * @param limitId The limit Id.
     * @return The status if a limit exists, otherwise `null`.
     */
    public fun status(limitId: String): Status? {
        val currentTime = clock.now()
        var result: Status? = null

        data.update { current ->
            val recordedHits = current.hits[limitId] ?: return@update current
            val appliedRule = current.rules[limitId] ?: return@update current

            val updatedHits = filter(recordedHits, appliedRule, currentTime)

            // Calculate result while we have the data
            result = if (updatedHits.size >= appliedRule.rate) {
                val nextExpired = appliedRule.duration - (currentTime - updatedHits[updatedHits.size - appliedRule.rate])
                Status(LimitStatus.OVER, nextExpired)
            } else {
                Status(LimitStatus.UNDER, 0.seconds)
            }

            current.withHits(limitId, updatedHits)
        }

        return result
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
        data.update { current ->
            current.withRuleAndResetHits(limitId, Rule(rate, duration))
        }
    }

    private fun filter(hits: List<Instant>, rule: Rule, currentTime: Instant): List<Instant> {
        return hits.filter { it + rule.duration > currentTime }
    }

    /**
     * Limit status.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
