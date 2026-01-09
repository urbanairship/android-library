/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField

public data class AttributeName public constructor(
    public val channel: String?,
    public val contact: String?
) {
    public val isChannel: Boolean = !channel.isNullOrEmpty()
    public val isContact: Boolean = !contact.isNullOrEmpty()

    public companion object {
        private const val KEY_CHANNEL = "channel"
        private const val KEY_CONTACT = "contact"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): AttributeName? {
            val content = json.requireMap()

            val channel = content.optionalField<String>(KEY_CHANNEL)
            val contact = content.optionalField<String>(KEY_CONTACT)

            if (channel == null && contact == null) {
                return null
            }

            return AttributeName(channel, contact)
        }

        public fun attributeNameFromJson(json: JsonMap): AttributeName? {
            return try {
                fromJson(json.opt("attribute_name"))
            } catch (_: Exception) {
                null
            }
        }
    }
}
