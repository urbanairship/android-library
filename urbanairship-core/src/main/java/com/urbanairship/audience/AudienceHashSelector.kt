/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.Clock
import java.time.Instant
import java.util.Objects

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AudienceHashSelector internal constructor(
    internal val hash: AudienceHash,
    internal val bucket: BucketSubset,
    internal val sticky: AudienceSticky? = null,
    internal val overrides: List<AudienceSubsetOverride>? = null
) : JsonSerializable {

    internal companion object {
        private const val KEY_HASH = "audience_hash"
        private const val KEY_BUCKET_SUBSET = "audience_subset"
        private const val KEY_STICKY = "sticky"
        private const val KEY_SUBSET_OVERRIDES = "audience_subset_overrides"

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
                    sticky = json[KEY_STICKY]?.let(AudienceSticky::fromJson),
                    overrides = json.opt(KEY_SUBSET_OVERRIDES).list
                        ?.mapNotNull { AudienceSubsetOverride.fromJson(it.optMap()) }
                )
            } catch (ex: JsonException) {
                UALog.e { "failed to parse AudienceSelector from json $json" }
                return null
            }
        }
    }

    internal fun evaluate(
        channelId: String,
        contactId: String,
        now: Instant = Clock.DEFAULT_CLOCK.now()
    ): Boolean {
        val properties = mapOf(
            HashIdentifiers.CONTACT.jsonValue to contactId,
            HashIdentifiers.CHANNEL.jsonValue to channelId
        )

        val effectiveBucket = effectiveBucket(now)

        return hash
            .generate(properties)
            ?.let { effectiveBucket.contains(it) }
            ?: false
    }

    /**
     * Resolves the effective audience subset for the given time. Walks [overrides] in order and
     * returns the first whose schedule is active; otherwise falls back to the base [bucket].
     */
    private fun effectiveBucket(now: Instant): BucketSubset {
        val overrides = this.overrides ?: return bucket
        return overrides.firstOrNull { it.schedule.isActive(now) }?.resolveBucket(now) ?: bucket
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_HASH to hash,
        KEY_BUCKET_SUBSET to bucket,
        KEY_STICKY to sticky,
        KEY_SUBSET_OVERRIDES to overrides
    ).toJsonValue()

    override fun toString(): String {
        return "AudienceHashSelector(hash=$hash, bucket=$bucket, sticky: $sticky, overrides=$overrides)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudienceHashSelector

        if (hash != other.hash) return false
        if (bucket != other.bucket) return false
        if (sticky != other.sticky) return false
        if (overrides != other.overrides) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(hash, bucket, sticky, overrides)
    }
}
