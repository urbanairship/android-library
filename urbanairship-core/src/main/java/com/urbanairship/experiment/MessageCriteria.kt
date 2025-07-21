package com.urbanairship.experiment

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue

internal data class MessageCriteria(
    val messageTypePredicate: JsonPredicate?,
    val campaignPredicate: JsonPredicate?
) {

    companion object {
        private const val KEY_PREDICATE = "message_type"
        private const val KEY_PREDICATE_CAMPAIGNS = "campaigns"

        /**
         * Creates a `MessageCriteria` object a [JsonMap].
         *
         * @param json A Remote Data payload containing configuration for an MessageCriteria.
         *
         * @hide
         */
        internal fun fromJson(json: JsonMap): MessageCriteria? {
            try {

                val message = if (json.containsKey(KEY_PREDICATE))
                    json.opt(KEY_PREDICATE).let(JsonPredicate::parse)
                else null

                val campaign = if (json.containsKey(KEY_PREDICATE_CAMPAIGNS))
                    json.opt(KEY_PREDICATE_CAMPAIGNS).let(JsonPredicate::parse)
                else null

                return MessageCriteria(
                    messageTypePredicate = message,
                    campaignPredicate = campaign
                )
            } catch (ex: JsonException) {
                UALog.e { "failed to parse MessageCriteria from json $json" }
                return null
            }
        }
    }

    fun evaluate(info: MessageInfo): Boolean {
        val messageType = messageTypePredicate?.apply(JsonValue.wrap(info.messageType)) ?: false
        val campaigns = campaignPredicate?.apply(info.campaigns ?: JsonValue.NULL) ?: false

        return messageType || campaigns
    }
}
