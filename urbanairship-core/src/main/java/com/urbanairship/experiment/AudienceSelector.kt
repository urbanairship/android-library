package com.urbanairship.experiment

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal class AudienceSelector(
    val hash: AudienceHash,
    val bucket: BucketSubset
) {

    companion object {
        private const val KEY_HASH = "audience_hash"
        private const val KEY_BUCKET_SUBSET = "audience_subset"

        /**
         * Creates a `AudienceSelector` object a [JsonMap].
         *
         * @param json A Remote Data payload containing configuration for an AudienceSelector.
         *
         * @hide
         */
        internal fun fromJson(json: JsonMap): AudienceSelector? {
            try {
                val hash = AudienceHash.fromJson(json.require(KEY_HASH).optMap())
                    ?: return null

                val bucket = BucketSubset.fromJson(json.require(KEY_BUCKET_SUBSET).optMap())
                    ?: return null

                return AudienceSelector(hash = hash, bucket = bucket)
            } catch (ex: JsonException) {
                UALog.e { "failed to parse AudienceSelector from json $json" }
                return null
            }
        }
    }
}
