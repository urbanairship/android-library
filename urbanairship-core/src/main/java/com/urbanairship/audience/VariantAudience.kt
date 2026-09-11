/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
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

    /**
     * Where a device's resolved hash bucket falls within the experiment.
     *
     * Modelled as a sealed class rather than an enum so an outcome stamped by a newer SDK
     * still reads back: the resolution is persisted at prepare time and re-read at execute
     * time, and rejecting the value would take the whole stored schedule with it.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public sealed class Outcome(public val json: String) {
        /** The bucket falls in this schedule's own arm: proceed with a normal execution. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public data object MATCHED : Outcome("matched")

        /** The bucket falls in the experiment's shared no-message arm. */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public data object HOLDOUT : Outcome("holdout")

        /**
         * The bucket falls in neither this arm nor the holdout arm — some sibling schedule's
         * arm owns it.
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public data object VARIANT_MISS : Outcome("variant_miss")

        /**
         * An outcome this SDK version does not recognize, carrying the value it was stamped
         * with so a rewrite round-trips it rather than flattening it to a placeholder.
         * Display is skipped: a schedule whose resolution this version can't read must not
         * display on the strength of not understanding it.
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public data class Unknown(public val rawValue: String) : Outcome(rawValue)

        /** True for every outcome but [MATCHED] — the ones where this schedule does not display. */
        public val isDisplaySkipped: Boolean
            get() = this != MATCHED

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public companion object {
            /**
             * Every outcome this SDK version recognizes.
             *
             * Deliberately lazy: building this list eagerly would read the nested objects
             * from the companion's own initializer, which runs while the sealed class is
             * still initializing, and each element would come back null.
             */
            private val known: List<Outcome> by lazy { listOf(MATCHED, HOLDOUT, VARIANT_MISS) }

            /** Never rejects an unrecognized outcome: it becomes [Unknown]. */
            public fun from(value: String): Outcome =
                known.firstOrNull { it.json == value } ?: Unknown(value)
        }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        private const val KEY_HASH = "audience_hash"
        private const val KEY_AUDIENCE_SUBSET = "audience_subset"
        private const val KEY_HOLDOUT_SUBSET = "holdout_subset"
        private const val KEY_REPORTING_CONTEXT = "reporting_context"

        /**
         * Parses a `variant_audience` payload.
         *
         * Throws rather than returning null for a payload it can't read. A schedule whose
         * experiment this version can't evaluate must not fall back to displaying to
         * everyone, so the failure has to reach the caller and take the schedule with it.
         *
         * @param json The `variant_audience` payload.
         * @return The parsed [VariantAudience].
         * @throws JsonException if any part of the payload is missing or unreadable.
         */
        @Throws(JsonException::class)
        public fun fromJson(json: JsonMap): VariantAudience {
            val hash = AudienceHash.fromJson(json.require(KEY_HASH).requireMap())
                ?: throw JsonException("Invalid variant audience hash in $json")

            val audienceSubset = BucketSubset.fromJson(json.require(KEY_AUDIENCE_SUBSET).requireMap())
                ?: throw JsonException("Invalid variant audience subset in $json")

            return VariantAudience(
                hash = hash,
                audienceSubset = audienceSubset,
                holdoutSubset = json[KEY_HOLDOUT_SUBSET]?.let {
                    BucketSubset.fromJson(it.requireMap())
                        ?: throw JsonException("Invalid variant audience holdout subset in $json")
                },
                // Dropped rather than rejected when it isn't an object, unlike everything
                // above: it only rides along on reporting events, so a malformed one costs
                // reporting fidelity while rejecting it would cost the whole schedule.
                reportingContext = json[KEY_REPORTING_CONTEXT]?.map
            )
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
