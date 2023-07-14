/* Copyright Airship and Contributors */

package com.urbanairship.audience

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

internal class AudienceHashSelector(
    val hash: AudienceHash,
    val bucket: BucketSubset
) : JsonSerializable {

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
        internal fun fromJson(json: JsonMap): AudienceHashSelector? {
            try {
                val hash = AudienceHash.fromJson(json.require(KEY_HASH).optMap())
                    ?: return null

                val bucket = BucketSubset.fromJson(json.require(KEY_BUCKET_SUBSET).optMap())
                    ?: return null

                return AudienceHashSelector(hash = hash, bucket = bucket)
            } catch (ex: JsonException) {
                UALog.e { "failed to parse AudienceSelector from json $json" }
                return null
            }
        }
    }

    internal fun evaluate(channelId: String, contactId: String): Boolean {
        val properties = mapOf(
            HashIdentifiers.CONTACT.jsonValue to contactId,
            HashIdentifiers.CHANNEL.jsonValue to channelId
        )

        return hash
            .generate(properties)
            ?.let { bucket.contains(it) }
            ?: false
    }

    override fun toJsonValue(): JsonValue {
        return JsonMap.newBuilder()
            .put(KEY_HASH, hash.toJsonValue())
            .put(KEY_BUCKET_SUBSET, bucket.toJsonValue())
            .build()
            .toJsonValue()
    }

    override fun toString(): String {
        return "AudienceHashSelector(hash=$hash, bucket=$bucket)"
    }
}
