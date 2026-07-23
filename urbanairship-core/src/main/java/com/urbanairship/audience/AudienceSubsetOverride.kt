/* Copyright Airship and Contributors */

package com.urbanairship.audience

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField
import kotlin.math.floor

/**
 * A time-based override for an [AudienceHashSelector]'s effective audience subset. Used by
 * automated rollouts so the rollout percentage can ramp automatically over time, computed
 * on-device from a schedule baked into the payload.
 *
 * The schedule window shares the JSON object with the override fields (the `start_timestamp` /
 * `end_timestamp` keys are flattened alongside `type` and the subset keys).
 */
internal sealed class AudienceSubsetOverride : JsonSerializable {

    abstract val schedule: TimeSpan

    data class Static(
        override val schedule: TimeSpan,
        val subset: BucketSubset
    ) : AudienceSubsetOverride()

    data class LinearRamp(
        override val schedule: TimeSpan,
        val subsetStart: BucketSubset,
        val subsetEnd: BucketSubset
    ) : AudienceSubsetOverride()

    /** Resolves the effective bucket for this override at the given time (ms since epoch). */
    fun resolveBucket(now: Long): BucketSubset = when (this) {
        is Static -> subset
        is LinearRamp -> interpolate(this, now)
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_SUBSET = "audience_subset"
        private const val KEY_SUBSET_START = "audience_subset_start"
        private const val KEY_SUBSET_END = "audience_subset_end"

        private const val TYPE_STATIC = "static"
        private const val TYPE_LINEAR_RAMP = "linear_ramp"

        internal fun fromJson(json: JsonMap): AudienceSubsetOverride? {
            return try {
                // The schedule window shares the container with the override fields.
                val schedule = TimeSpan.fromJson(json)
                when (val type = json.requireField<String>(KEY_TYPE)) {
                    TYPE_STATIC -> Static(
                        schedule = schedule,
                        subset = BucketSubset.fromJson(json.require(KEY_SUBSET).optMap())
                            ?: return null
                    )
                    TYPE_LINEAR_RAMP -> {
                        if (schedule.startTimestamp == null || schedule.endTimestamp == null) {
                            UALog.e { "linear_ramp override requires start and end timestamps: $json" }
                            return null
                        }
                        LinearRamp(
                            schedule = schedule,
                            subsetStart = BucketSubset.fromJson(json.require(KEY_SUBSET_START).optMap())
                                ?: return null,
                            subsetEnd = BucketSubset.fromJson(json.require(KEY_SUBSET_END).optMap())
                                ?: return null
                        )
                    }
                    else -> {
                        UALog.e { "unknown audience_subset_override type: $type" }
                        null
                    }
                }
            } catch (ex: JsonException) {
                UALog.e { "failed to parse AudienceSubsetOverride from json $json" }
                null
            }
        }

        private fun interpolate(ramp: LinearRamp, now: Long): BucketSubset {
            val startMs = ramp.schedule.startTimestamp
            val endMs = ramp.schedule.endTimestamp
            if (startMs == null || endMs == null || endMs <= startMs) {
                return ramp.subsetEnd
            }

            val t = ((now - startMs).toDouble() / (endMs - startMs).toDouble()).coerceIn(0.0, 1.0)

            return BucketSubset(
                min = interpolateBucket(ramp.subsetStart.min, ramp.subsetEnd.min, t),
                max = interpolateBucket(ramp.subsetStart.max, ramp.subsetEnd.max, t)
            )
        }

        /**
         * Casts to [Double] before subtracting so an inverted (start > end) subset can never
         * underflow in unsigned integer arithmetic.
         */
        private fun interpolateBucket(from: ULong, to: ULong, t: Double): ULong {
            val value = floor(from.toDouble() + t * (to.toDouble() - from.toDouble()))
            return value.toULong()
        }
    }

    override fun toJsonValue(): JsonValue {
        val builder = JsonMap.newBuilder().putAll(schedule.toJsonValue().optMap())
        return when (this) {
            is Static -> builder
                .put(KEY_TYPE, TYPE_STATIC)
                .put(KEY_SUBSET, subset)
            is LinearRamp -> builder
                .put(KEY_TYPE, TYPE_LINEAR_RAMP)
                .put(KEY_SUBSET_START, subsetStart)
                .put(KEY_SUBSET_END, subsetEnd)
        }.build().toJsonValue()
    }
}
