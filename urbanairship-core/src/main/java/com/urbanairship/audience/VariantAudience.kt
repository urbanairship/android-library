/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Objects

/**
 * Makes an in-app schedule one arm of an Experiment Groups variant experiment.
 *
 * Every schedule sharing an experiment is given an identical `audienceHash`, so hashing it
 * resolves the same bucket for a given device no matter which sibling schedule asks. Reuses
 * [AudienceHash]/[BucketSubset], the same hash/bucket primitives [AudienceHashSelector] uses,
 * rather than a second hashing mechanism.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VariantAudience internal constructor(
    private val hash: AudienceHash,
    private val audienceSubset: BucketSubset,
    private val holdoutSubset: BucketSubset?,
    /**
     * Opaque context appended to the `experiments` array on this schedule's reporting events.
     *
     * A `variant_miss` event carries nothing else that names its experiment, so the platform
     * is expected to author this identically across every schedule in the experiment.
     */
    public val reportingContext: JsonMap? = null
) : JsonSerializable {

    /** Where a device's resolved hash bucket falls within the experiment. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public enum class Outcome(public val json: String) {
        /** The bucket falls in this schedule's own arm: proceed with a normal execution. */
        MATCHED("matched"),

        /** The bucket falls in the experiment's shared no-message arm. */
        HOLDOUT("holdout"),

        /**
         * The bucket falls in neither this arm nor the holdout arm — some sibling schedule's
         * arm owns it.
         */
        VARIANT_MISS("variant_miss");

        /**
         * True for [HOLDOUT] and [VARIANT_MISS] — the outcomes where this schedule does not
         * display.
         */
        public val isDisplaySkipped: Boolean
            get() = this != MATCHED

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public companion object {
            public fun from(value: String): Outcome? = entries.firstOrNull { it.json == value }
        }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        private const val KEY_HASH = "audience_hash"
        private const val KEY_AUDIENCE_SUBSET = "audience_subset"
        private const val KEY_HOLDOUT_SUBSET = "holdout_subset"
        private const val KEY_REPORTING_CONTEXT = "reporting_context"

        public fun fromJson(json: JsonMap): VariantAudience? {
            try {
                val hash = AudienceHash.fromJson(json.require(KEY_HASH).optMap())
                    ?: return null

                val audienceSubset = BucketSubset.fromJson(json.require(KEY_AUDIENCE_SUBSET).optMap())
                    ?: return null

                return VariantAudience(
                    hash = hash,
                    audienceSubset = audienceSubset,
                    holdoutSubset = json[KEY_HOLDOUT_SUBSET]?.let { BucketSubset.fromJson(it.optMap()) },
                    reportingContext = json[KEY_REPORTING_CONTEXT]?.map
                )
            } catch (ex: JsonException) {
                UALog.e { "failed to parse VariantAudience from json $json" }
                return null
            }
        }
    }

    /**
     * Resolves this schedule's outcome within its variant experiment.
     *
     * @param channelId The device's channel ID.
     * @param contactId The device's contact ID.
     * @return The resolved [Outcome].
     */
    public fun resolve(channelId: String, contactId: String): Outcome {
        if (AudienceHashSelector(hash = hash, bucket = audienceSubset)
                .evaluate(channelId = channelId, contactId = contactId)) {
            return Outcome.MATCHED
        }

        if (holdoutSubset != null &&
            AudienceHashSelector(hash = hash, bucket = holdoutSubset)
                .evaluate(channelId = channelId, contactId = contactId)) {
            return Outcome.HOLDOUT
        }

        return Outcome.VARIANT_MISS
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_HASH to hash,
        KEY_AUDIENCE_SUBSET to audienceSubset,
        KEY_HOLDOUT_SUBSET to holdoutSubset,
        KEY_REPORTING_CONTEXT to reportingContext
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VariantAudience

        if (hash != other.hash) return false
        if (audienceSubset != other.audienceSubset) return false
        if (holdoutSubset != other.holdoutSubset) return false
        if (reportingContext != other.reportingContext) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(hash, audienceSubset, holdoutSubset, reportingContext)
}
