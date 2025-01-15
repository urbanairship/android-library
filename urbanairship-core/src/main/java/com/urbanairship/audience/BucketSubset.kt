package com.urbanairship.audience

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import java.util.Objects

internal class BucketSubset(
    val min: ULong,
    val max: ULong
) : JsonSerializable {

    companion object {
        private const val KEY_BUCKET_MIN = "min_hash_bucket"
        private const val KEY_BUCKET_MAX = "max_hash_bucket"

        /**
         * Creates a `ExperimentBucket` object a [JsonMap].
         *
         * @param json A Remote Data payload containing configuration for an ExperimentBucket.
         *
         * @hide
         */
        internal fun fromJson(json: JsonMap): BucketSubset? {
            val converted: (Long) -> ULong = { it.toULong() }
            try {
                return BucketSubset(
                    min = converted(json.optionalField(KEY_BUCKET_MIN) ?: 0),
                    max = converted(json.optionalField(KEY_BUCKET_MAX) ?: Long.MAX_VALUE)
                )
            } catch (ex: JsonException) {
                UALog.e { "failed to parse ExperimentBucket from json $json" }
                return null
            }
        }
    }

    fun contains(value: ULong): Boolean {
        return value in min..max
    }

    override fun toJsonValue(): JsonValue {
        return JsonMap.newBuilder()
            .put(KEY_BUCKET_MIN, min.toLong())
            .put(KEY_BUCKET_MAX, max.toLong())
            .build().toJsonValue()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BucketSubset

        if (min != other.min) return false
        if (max != other.max) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(min, max)
    }
}
