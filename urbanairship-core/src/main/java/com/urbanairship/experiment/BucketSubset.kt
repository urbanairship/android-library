package com.urbanairship.experiment

import com.urbanairship.Logger
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField

internal class BucketSubset(
    val min: Int?,
    val max: Int?
) {

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
            try {
                return BucketSubset(
                    min = json.optionalField(KEY_BUCKET_MIN),
                    max = json.optionalField(KEY_BUCKET_MAX)
                )
            } catch (ex: JsonException) {
                Logger.e { "failed to parse ExperimentBucket from json $json" }
                return null
            }
        }
    }
}
