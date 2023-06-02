package com.urbanairship.experiment

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField

internal data class EvaluationOptions(
    val disallowStaleValue: Boolean?,
    val disallowStaleContact: Boolean?,
    val ttl: Int?
) {
    companion object {
        private const val KEY_DISALLOW_STALE_VALUE = "disallow_stale_value"
        private const val KEY_DISALLOW_STALE_CONTACT = "disallow_stale_contact"
        private const val KEY_TTL = "ttl"

        /**
         * Creates a `EvaluationOptions` object a [JsonMap].
         *
         * @param json A Remote Data payload containing configuration for an EvaluationOptions.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        internal fun fromJson(json: JsonMap): EvaluationOptions? {
            try {
                return EvaluationOptions(
                    disallowStaleValue = json.optionalField(KEY_DISALLOW_STALE_VALUE),
                    disallowStaleContact = json.optionalField(KEY_DISALLOW_STALE_CONTACT),
                    ttl = json.optionalField(KEY_TTL)
                )
            } catch (ex: JsonException) {
                UALog.e { "failed to parse EvaluationOptions from json $json" }
                return null
            }
        }
    }
}
