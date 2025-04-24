/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import androidx.annotation.RestrictTo
import androidx.annotation.Size
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.analytics.templates.AccountEventTemplate
import com.urbanairship.analytics.templates.MediaEventTemplate
import com.urbanairship.analytics.templates.RetailEventTemplate
import com.urbanairship.analytics.templates.SearchEventTemplate
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.toJsonMap
import com.urbanairship.push.PushMessage
import com.urbanairship.util.UAStringUtil
import java.math.BigDecimal

/**
 * A class that represents a custom event for the application.
 */
public class CustomEvent private constructor(
    /** Event name. */
    public val eventName: String,
    /** The event value. */
    public val eventValue: BigDecimal?,
    /** The transaction ID. */
    public val transactionId: String? = null,
    /** The interaction type. */
    public val interactionType: String? = null,
    /** The interaction ID. */
    public val interactionId: String? = null,
    /** The event properties. */
    public val properties: JsonMap,

    internal val sendId: String? = null,
    internal val templateType: String? = null,
    internal val inAppContext: JsonValue? = null
) : Event(), JsonSerializable {

    private constructor(builder: Builder): this(
        eventName = builder.eventName,
        eventValue = builder.value,
        transactionId = builder.transactionId,
        interactionType = builder.interactionType,
        interactionId = builder.interactionId,
        sendId = builder.pushSendId,
        templateType = builder.templateType,
        properties = builder.properties.toJsonMap(),
        inAppContext = builder.inAppContext
    )

    override fun getType(): EventType = EventType.CUSTOM_EVENT

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getEventData(conversionData: ConversionData): JsonMap {
        val data = JsonMap.newBuilder()

        val conversionSendId = conversionData.conversionSendId
        val conversionMetadata = conversionData.conversionMetadata

        data.put(EVENT_NAME, eventName)
        data.put(INTERACTION_ID, interactionId)
        data.put(INTERACTION_TYPE, interactionType)
        data.put(TRANSACTION_ID, transactionId)
        data.put(TEMPLATE_TYPE, templateType)
        data.put(IN_APP_KEY, inAppContext)

        data.putOpt(EVENT_VALUE, eventValue?.movePointRight(6)?.toLong())

        if (!UAStringUtil.isEmpty(sendId)) {
            data.put(CONVERSION_SEND_ID, sendId)
        } else {
            data.put(CONVERSION_SEND_ID, conversionSendId)
        }

        if (conversionMetadata != null) {
            data.put(CONVERSION_METADATA, conversionMetadata)
        } else {
            data.put(LAST_RECEIVED_METADATA, conversionData.lastReceivedMetadata)
        }

        if (properties.map.isNotEmpty()) {
            data.put(PROPERTIES, properties)
        }

        return data.build()
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        EVENT_NAME to eventName,
        EVENT_VALUE to eventValue?.toDouble(),
        INTERACTION_ID to interactionId,
        INTERACTION_TYPE to interactionType,
        TRANSACTION_ID to transactionId,
        IN_APP_KEY to inAppContext,
        PROPERTIES to properties
    ).toJsonValue()


    override fun isValid(): Boolean {

        if (UAStringUtil.isEmpty(eventName) || !eventName.isLengthValid()) {
            UALog.e(
                "Event name must not be null, empty, or larger than $MAX_CHARACTER_LENGTH characters."
            )
            return false
        }

        eventValue?.let { value ->
            if (value > MAX_VALUE) {
                UALog.e("Event value is bigger than $MAX_VALUE")
                return false
            }

            if (value < MIN_VALUE) {
                UALog.e("Event value is smaller than $MIN_VALUE")
                return false
            }
        }

        if (transactionId?.isLengthValid() == false) {
            UALog.e("Transaction ID is larger than $MAX_CHARACTER_LENGTH characters.")
            return false
        }

        if (interactionId?.isLengthValid() == false) {
            UALog.e("Interaction ID is larger than $MAX_CHARACTER_LENGTH characters.")
            return false
        }

        if (interactionType?.isLengthValid() == false) {
            UALog.e("Interaction type is larger than $MAX_CHARACTER_LENGTH characters.")
            return false
        }

        if (templateType?.isLengthValid() == false) {
            UALog.e("Template type is larger than $MAX_CHARACTER_LENGTH characters.")
            return false
        }

        if (properties.sizeInBytes() > MAX_TOTAL_PROPERTIES_SIZE) {
            UALog.e(
                "Total custom properties size (${properties.sizeInBytes()} bytes) " +
                        "exceeds maximum size of $MAX_TOTAL_PROPERTIES_SIZE bytes.",
            )
            return false
        }

        return true
    }

    /**
     * Adds the event to Analytics.
     *
     * @return The tracked custom event.
     */
    public fun track(): CustomEvent {
        UAirship.shared().analytics.recordCustomEvent(this)
        return this
    }

    /**
     * Builder class for [com.urbanairship.analytics.CustomEvent] Objects.
     */
    public class Builder
    /**
     * Creates a new custom event builder
     *
     *
     * The event name must be between 1 and 255 characters or the event will be invalid.
     *
     * @param eventName The name of the event.
     * @throws java.lang.IllegalArgumentException if the event name is null, empty, or exceeds 255
     * characters.
     */ public constructor(
        @param:Size(min = 1, max = MAX_CHARACTER_LENGTH.toLong())
        public val eventName: String
    ) {

        public var value: BigDecimal? = null
            private set

        public var transactionId: String? = null
            private set

        public var interactionType: String? = null
            private set

        public var interactionId: String? = null
            private set

        public var pushSendId: String? = null
            private set

        public var templateType: String? = null
            private set

        public var properties: MutableMap<String, JsonValue> = HashMap()
            private set

        public var inAppContext: JsonValue? = null
            private set

        /**
         * Sets a JsonMap representing the event properties.
         *
         * If the total added properties exceed [MAX_TOTAL_PROPERTIES_SIZE] in size it will cause the event
         * to be invalid.
         *
         * @param properties A JsonMap of the event's properties.
         * @return The custom event builder.
         */
        public fun setProperties(properties: JsonMap?): Builder {
            return this.also {
                if (properties == null) {
                    it.properties.clear()
                } else {
                    it.properties.putAll(properties.map)
                }
            }
        }

        /**
         * Sets the event value.
         *
         * The event's value will be accurate 6 digits after the decimal. The number must fall in the
         * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
         *
         * @param value The event's value as a BigDecimal.
         * @return The custom event builder.
         */
        public fun setEventValue(value: BigDecimal?): Builder {
            return this.also { it.value = value }
        }

        /**
         * Sets the event value.
         *
         * The event's value will be accurate 6 digits after the decimal. The number must fall in the
         * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
         *
         * @param value The event's value as a double
         * @return The custom event builder.
         * @throws java.lang.NumberFormatException if the value is infinity or not a number.
         */
        @Throws(NumberFormatException::class)
        public fun setEventValue(value: Double): Builder {
            return setEventValue(BigDecimal.valueOf(value))
        }

        /**
         * Sets the event value from a String.
         *
         * The event's value will be accurate 6 digits after the decimal. The number must fall in the
         * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
         *
         * @param value The event's value as a String.
         * @return The custom event builder.
         * @throws java.lang.NumberFormatException if the event value does not contain a valid string representation
         * of a big decimal.
         */
        @Throws(NumberFormatException::class)
        public fun setEventValue(value: String?): Builder {
            if (UAStringUtil.isEmpty(value)) {
                return this.also { it.value = null }
            }

            return setEventValue(BigDecimal(value))
        }

        /**
         * Sets the event value.
         *
         * @param value The event's value as an int.
         * @return The custom event builder.
         */
        public fun setEventValue(value: Int): Builder {
            return setEventValue(BigDecimal(value))
        }

        /**
         * Sets the transaction ID.
         *
         * If the transaction ID exceeds [MAX_CHARACTER_LENGTH] characters it will cause the event to be invalid.
         *
         * @param transactionId The event's transaction ID.
         * @return The custom event builder.
         */
        public fun setTransactionId(
            @Size(min = 1, max = MAX_CHARACTER_LENGTH.toLong()) transactionId: String?
        ): Builder {
            return this.also { this.transactionId = transactionId }
        }

        /**
         * Sets the interaction type and ID from a Message Center message.
         *
         * @param richPushMessageId The rich push message ID that created the custom event.
         * @return The custom event builder.
         */
        public fun setMessageCenterInteraction(richPushMessageId: String): Builder {
            return this.also {
                it.interactionType = MCRAP_TRANSACTION_TYPE
                it.interactionId = richPushMessageId
            }
        }

        /**
         * Sets the interaction type and ID for the event.
         *
         * If any non-property field exceeds [MAX_CHARACTER_LENGTH] characters it will cause the event to be invalid.
         *
         * @param interactionType The event's interaction type.
         * @param interactionId The event's interaction ID.
         * @return The custom event builder.
         */
        public fun setInteraction(
            @Size(min = 1, max = MAX_CHARACTER_LENGTH.toLong()) interactionType: String?,
            @Size(min = 1, max = MAX_CHARACTER_LENGTH.toLong()) interactionId: String?
        ): Builder {
            return this.also {
                it.interactionId = interactionId
                it.interactionType = interactionType
            }
        }

        /**
         * Sets the attribution from a specific push message.
         *
         * @param pushMessage The attributing push message.
         * @return The custom event builder.
         * @hide
         */
        public fun setAttribution(pushMessage: PushMessage?): Builder {
            return this.also { pushMessage?.let { id -> it.pushSendId = id.sendId } }
        }

        /**
         * Sets the template type for the event.
         *
         * @param templateType The event's template type.
         * @return The custom event builder.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun setTemplateType(
            @Size(min = 1, max = MAX_CHARACTER_LENGTH.toLong()) templateType: String?
        ): Builder {
            return this.also { it.templateType = templateType }
        }

        /**
         * Adds a custom property to the event.
         *
         * If the total added properties exceed [MAX_TOTAL_PROPERTIES_SIZE] in size it will cause the event
         * to be invalid.
         *
         * @param name The property name.
         * @param value A property value.
         * @return The custom event builder.
         */
        public fun addProperty(
            @Size(min = 1) name: String,
            value: JsonSerializable
        ): Builder {
            return this.also { properties[name] = value.toJsonValue() }
        }

        /**
         * Adds a custom property to the event.
         *
         * If the total added properties exceed [MAX_TOTAL_PROPERTIES_SIZE] in size it will cause the event
         * to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         */
        public fun addProperty(
            @Size(min = 1) name: String,
            @Size(min = 1) value: String
        ): Builder {
            return this.also { properties[name] = JsonValue.wrap(value) }
        }

        /**
         * Adds a custom property to the event.
         *
         * If the total added properties exceed [MAX_TOTAL_PROPERTIES_SIZE] in size it will cause the event
         * to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         */
        public fun addProperty(
            @Size(min = 1) name: String,
            value: Int
        ): Builder {
            return this.also { properties[name] = JsonValue.wrap(value) }
        }

        /**
         * Adds a custom property to the event.
         *
         * If the total added properties exceed [MAX_TOTAL_PROPERTIES_SIZE] in size it will cause the event
         * to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         */
        public fun addProperty(
            @Size(min = 1) name: String,
            value: Long
        ): Builder {
            return this.also { properties[name] = JsonValue.wrap(value) }
        }

        /**
         * Adds a custom property to the event.
         *
         * If the total number of properties exceed [MAX_TOTAL_PROPERTIES_SIZE] it will cause the event
         * to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         * @throws java.lang.NumberFormatException if the value is infinite or not a number
         */
        @Throws(NumberFormatException::class)
        public fun addProperty(
            @Size(min = 1) name: String,
            value: Double
        ): Builder {
            if (java.lang.Double.isNaN(value) || java.lang.Double.isInfinite(value)) {
                throw NumberFormatException("Infinity or NaN: $value")
            }

            return this.also { properties[name] = JsonValue.wrap(value) }
        }

        /**
         * Adds a custom property to the event.
         *
         * If the total number of properties exceed [MAX_TOTAL_PROPERTIES_SIZE] it will cause the event
         * to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         */
        public fun addProperty(
            @Size(min = 1) name: String,
            value: Boolean
        ): Builder {
            return this.also { properties[name] = JsonValue.wrap(value) }
        }

        /**
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun setInAppContext(value: JsonValue?): Builder {
            return this.also { it.inAppContext = value }
        }

        /**
         * Builds the custom event.
         *
         * @return The built custom event.
         */
        public fun build(): CustomEvent {
            return CustomEvent(this)
        }
    }

    public companion object {

        /**
         * The interaction ID key.
         */
        public const val INTERACTION_ID: String = "interaction_id"

        /**
         * The interaction type key.
         */
        public const val INTERACTION_TYPE: String = "interaction_type"

        /**
         * The event name key.
         */
        public const val EVENT_NAME: String = "event_name"

        /**
         * The event value key.
         */
        public const val EVENT_VALUE: String = "event_value"

        /**
         * The event transaction id key.
         */
        public const val TRANSACTION_ID: String = "transaction_id"

        /**
         * Rich Push Message interaction type.
         */
        public const val MCRAP_TRANSACTION_TYPE: String = "ua_mcrap"

        /**
         * Hard conversion send id key.
         */
        public const val CONVERSION_SEND_ID: String = "conversion_send_id"

        /**
         * Hard conversion send metadata key.
         */
        public const val CONVERSION_METADATA: String = "conversion_metadata"

        /**
         * Last send metadata key.
         */
        public const val LAST_RECEIVED_METADATA: String = "last_received_metadata"

        /**
         * The template type key.
         */
        public const val TEMPLATE_TYPE: String = "template_type"

        private const val IN_APP_KEY = "in_app"

        /**
         * The custom properties key.
         */
        public const val PROPERTIES: String = "properties"

        /**
         * Max value allowed for the event value before it is converted to a long.
         */
        private val MAX_VALUE = BigDecimal(Int.MAX_VALUE)

        /**
         * Min value allowed for the event value before it is converted to a long.
         */
        private val MIN_VALUE = BigDecimal(Int.MIN_VALUE)

        /**
         * The max size for any String event value.
         */
        public const val MAX_CHARACTER_LENGTH: Int = 255

        /**
         * The max size of total properties in bytes.
         */
        public const val MAX_TOTAL_PROPERTIES_SIZE: Int = 65536

        /**
         * Creates a new CustomEvent builder.
         *
         * @param name The event name
         * @return The CustomEvent builder.
         */
        @JvmStatic
        public fun newBuilder(name: String): Builder {
            return Builder(name)
        }

        /**
         * Creates a new CustomEvent builder from a media template
         *
         * @param type Event situation [MediaEventTemplate.Type]
         * @param properties The event properties [MediaEventTemplate.Properties]
         * @return The CustomEvent builder.
         */
        @JvmStatic
        @JvmOverloads
        public fun newBuilder(
            type: MediaEventTemplate.Type,
            properties: MediaEventTemplate.Properties = MediaEventTemplate.Properties()
        ): Builder {

            var updatedProperties = properties

            when(type) {
                is MediaEventTemplate.Type.Shared -> {
                    updatedProperties = MediaEventTemplate.Properties
                        .Builder(properties)
                        .setSource(type.source)
                        .setMedium(type.medium)
                        .build()
                }
                else -> {}
            }

            return newBuilder(type.eventName)
                .setTemplateType(MediaEventTemplate.TEMPLATE_NAME)
                .setProperties(updatedProperties.toJsonValue().optMap())
        }

        /**
         * Creates a new CustomEvent builder from an account template
         *
         * @param type Event situation [AccountEventTemplate.Type]
         * @param properties The event properties [AccountEventTemplate.Properties]
         * @return The CustomEvent builder.
         */
        @JvmStatic
        @JvmOverloads
        public fun newBuilder(
            type: AccountEventTemplate.Type,
            properties: AccountEventTemplate.Properties = AccountEventTemplate.Properties()
        ): Builder {
            return newBuilder(type.eventName)
                .setTemplateType(AccountEventTemplate.TEMPLATE_NAME)
                .setProperties(properties.toJsonValue().optMap())
        }

        /**
         * Creates a new CustomEvent builder from a retail template
         *
         * @param type Event situation [RetailEventTemplate.Type]
         * @param properties The event properties [RetailEventTemplate.Properties]
         * @return The CustomEvent builder.
         */
        @JvmStatic
        @JvmOverloads
        public fun newBuilder(
            type: RetailEventTemplate.Type,
            properties: RetailEventTemplate.Properties = RetailEventTemplate.Properties()
        ): Builder {
            var updatedProperties = properties

            when(type) {
                is RetailEventTemplate.Type.Shared -> {
                    updatedProperties = RetailEventTemplate.Properties
                        .Builder(properties)
                        .setSource(type.source)
                        .setMedium(type.medium).build()
                }
                is RetailEventTemplate.Type.Wishlist -> {
                    updatedProperties = RetailEventTemplate.Properties
                        .Builder(properties)
                        .setWhishlistName(type.name)
                        .setWishlistId(type.id)
                        .build()
                }
                else -> {}
            }

            return newBuilder(type.eventName)
                .setTemplateType(RetailEventTemplate.TEMPLATE_NAME)
                .setProperties(updatedProperties.toJsonValue().optMap())
        }

        /**
         * Creates a new CustomEvent builder from a search template
         *
         * @param type Event situation [SearchEventTemplate.Type]
         * @param properties The event properties [SearchEventTemplate.Properties]
         * @return The CustomEvent builder.
         */
        @JvmStatic
        @JvmOverloads
        public fun newBuilder(
            type: SearchEventTemplate.Type,
            properties: SearchEventTemplate.Properties = SearchEventTemplate.Properties()
        ): Builder {
            return newBuilder(type.eventName)
                .setTemplateType(SearchEventTemplate.TEMPLATE_NAME)
                .setProperties(properties.toJsonValue().optMap())
        }
    }
}

private fun String.isLengthValid(): Boolean {
    return this.length < CustomEvent.MAX_CHARACTER_LENGTH
}

private fun JsonMap.sizeInBytes(): Int {
    return toJsonValue().toString().toByteArray().size
}
