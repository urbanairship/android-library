/* Copyright Airship and Contributors */

package com.urbanairship.audience

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Objects

internal class AudienceHashSelector(
    val hash: AudienceHash,
    val bucket: BucketSubset,
    val sticky: AudienceSticky? = null
) : JsonSerializable {

    companion object {
        private const val KEY_HASH = "audience_hash"
        private const val KEY_BUCKET_SUBSET = "audience_subset"
        private const val KEY_STICKY = "sticky"

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

                return AudienceHashSelector(
                    hash = hash,
                    bucket = bucket,
                    sticky = json.get(KEY_STICKY)?.let(AudienceSticky::fromJson)
                )
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

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_HASH to hash,
        KEY_BUCKET_SUBSET to bucket,
        KEY_STICKY to sticky
    ).toJsonValue()

    override fun toString(): String {
        return "AudienceHashSelector(hash=$hash, bucket=$bucket, sticky: $sticky)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudienceHashSelector

        if (hash != other.hash) return false
        if (bucket != other.bucket) return false
        if (sticky != other.sticky) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(hash, bucket, sticky)
    }
}
