/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField

internal sealed class ContactOperation(
    private val type: Type,
    private val payload: JsonValue?,
) : JsonSerializable {

    override fun toJsonValue() = jsonMapOf(
            TYPE_KEY to type.name, PAYLOAD_KEY to payload
    ).toJsonValue()

    object Resolve : ContactOperation(Type.RESOLVE, null)
    object Reset : ContactOperation(Type.RESET, null)

    data class Verify(
        val dateMs: Long,
        val required: Boolean = false
    ) : ContactOperation(
            Type.VERIFY,
            jsonMapOf(
                    DATE_KEY to dateMs,
                    REQUIRED_KEY to required
            ).toJsonValue()
    ) {
        constructor(json: JsonMap) : this(
                json.requireField(DATE_KEY),
                json.requireField(REQUIRED_KEY)
        )
    }

    data class Identify(val identifier: String) :
            ContactOperation(Type.IDENTIFY, JsonValue.wrapOpt(identifier)) {

        constructor(json: JsonValue) : this(
                json.requireString()
        )
    }

    data class Update(
        val tags: List<TagGroupsMutation>? = null,
        val attributes: List<AttributeMutation>? = null,
        val subscriptions: List<ScopedSubscriptionListMutation>? = null,
    ) : ContactOperation(
            Type.UPDATE,
            jsonMapOf(
                    TAG_GROUP_MUTATIONS_KEY to tags,
                    ATTRIBUTE_MUTATIONS_KEY to attributes,
                    SUBSCRIPTION_LISTS_MUTATIONS_KEY to subscriptions
            ).toJsonValue()
    ) {
        constructor(json: JsonMap) : this(
                TagGroupsMutation.fromJsonList(json.opt(TAG_GROUP_MUTATIONS_KEY).optList()).ifEmpty { null },
                AttributeMutation.fromJsonList(json.opt(ATTRIBUTE_MUTATIONS_KEY).optList()).ifEmpty { null },
                ScopedSubscriptionListMutation.fromJsonList(json.opt(SUBSCRIPTION_LISTS_MUTATIONS_KEY).optList()).ifEmpty { null })
    }

    data class AssociateChannel(
        val channelId: String,
        val channelType: ChannelType
    ) : ContactOperation(
            Type.ASSOCIATE_CHANNEL,
            jsonMapOf(
                    CHANNEL_ID_KEY to channelId,
                    CHANNEL_TYPE_KEY to channelType.name,
            ).toJsonValue()
    ) {

        constructor(json: JsonMap) : this(
                channelId = json.requireField(CHANNEL_ID_KEY), channelType = try {
            ChannelType.valueOf(json.requireField(CHANNEL_TYPE_KEY))
        } catch (e: IllegalArgumentException) {
            throw JsonException("Invalid channel type $json", e)
        }
        )
    }

    data class RegisterEmail(
        val emailAddress: String,
        val options: EmailRegistrationOptions
    ) : ContactOperation(
            Type.REGISTER_EMAIL,
            jsonMapOf(
                    EMAIL_ADDRESS_KEY to emailAddress,
                    OPTIONS_KEY to options
            ).toJsonValue()
    ) {
        constructor(json: JsonMap) : this(
                json.requireField(EMAIL_ADDRESS_KEY),
                EmailRegistrationOptions.fromJson(json.requireField(OPTIONS_KEY))
        )
    }

    data class RegisterSms(
        val msisdn: String,
        val options: SmsRegistrationOptions
    ) : ContactOperation(
            Type.REGISTER_SMS,
            jsonMapOf(
                    MSISDN_KEY to msisdn,
                    OPTIONS_KEY to options
            ).toJsonValue()
    ) {
        constructor(json: JsonMap) : this(
                json.requireField(MSISDN_KEY),
                SmsRegistrationOptions.fromJson(json.requireField(OPTIONS_KEY))
        )
    }

    data class RegisterOpen(
        val address: String,
        val options: OpenChannelRegistrationOptions
    ) : ContactOperation(
            Type.REGISTER_EMAIL,
            jsonMapOf(
                    ADDRESS_KEY to address,
                    OPTIONS_KEY to options
            ).toJsonValue()
    ) {
        constructor(json: JsonMap) : this(
                json.requireField(MSISDN_KEY),
                OpenChannelRegistrationOptions.fromJson(json.requireField(OPTIONS_KEY))
        )
    }

    enum class Type {
        UPDATE, IDENTIFY, RESOLVE, RESET, REGISTER_EMAIL, REGISTER_SMS, REGISTER_OPEN_CHANNEL,
        ASSOCIATE_CHANNEL, VERIFY
    }

    internal companion object {
        @JvmStatic
        fun fromJson(json: JsonValue): ContactOperation {
            val map = json.requireMap()
            val type = try {
                Type.valueOf(map.requireField("type"))
            } catch (exception: Exception) {
                throw JsonException("Unknown type! $map", exception)
            }

            return when (type) {
                Type.RESOLVE -> Resolve
                Type.IDENTIFY -> Identify(map.requireField<JsonValue>(PAYLOAD_KEY))
                Type.RESET -> Reset
                Type.UPDATE -> Update(map.requireField<JsonMap>(PAYLOAD_KEY))
                Type.ASSOCIATE_CHANNEL -> AssociateChannel(map.requireField(PAYLOAD_KEY))
                Type.REGISTER_EMAIL -> RegisterEmail(map.requireField(PAYLOAD_KEY))
                Type.REGISTER_OPEN_CHANNEL -> RegisterOpen(map.requireField(PAYLOAD_KEY))
                Type.REGISTER_SMS -> RegisterSms(map.requireField(PAYLOAD_KEY))
                Type.VERIFY -> Verify(map.requireField<JsonMap>(PAYLOAD_KEY))
            }
        }

        private const val ADDRESS_KEY = "ADDRESS"
        private const val MSISDN_KEY = "MSISDN"
        private const val EMAIL_ADDRESS_KEY = "EMAIL_ADDRESS"
        private const val OPTIONS_KEY = "OPTIONS"
        private const val CHANNEL_ID_KEY = "CHANNEL_ID"
        private const val CHANNEL_TYPE_KEY = "CHANNEL_TYPE"
        private const val TAG_GROUP_MUTATIONS_KEY = "TAG_GROUP_MUTATIONS_KEY"
        private const val ATTRIBUTE_MUTATIONS_KEY = "ATTRIBUTE_MUTATIONS_KEY"
        private const val SUBSCRIPTION_LISTS_MUTATIONS_KEY = "SUBSCRIPTION_LISTS_MUTATIONS_KEY"
        private const val REQUIRED_KEY = "REQUIRED"
        private const val DATE_KEY = "DATE"

        const val TYPE_KEY = "TYPE_KEY"
        const val PAYLOAD_KEY = "PAYLOAD_KEY"
    }
}
