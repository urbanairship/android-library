package com.urbanairship.experiment

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate

internal data class MessageCriteria(
    val messageTypePredicate: JsonPredicate?
) {

    companion object {
        private const val KEY_PREDICATE = "message_type"

        /**
         * Creates a `MessageCriteria` object a [JsonMap].
         *
         * @param json A Remote Data payload containing configuration for an MessageCriteria.
         *
         * @hide
         */
        internal fun fromJson(json: JsonMap): MessageCriteria? {
            try {
                return MessageCriteria(
                    messageTypePredicate = JsonPredicate.parse(json.require(KEY_PREDICATE))
                )
            } catch (ex: JsonException) {
                UALog.e { "failed to parse MessageCriteria from json $json" }
                return null
            }
        }
    }
}
