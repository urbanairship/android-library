package com.urbanairship.preferencecenter.data

import com.urbanairship.contacts.Scope
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.json.toJsonList

/**
 * Preference items.
 */
sealed class Item(private val type: String) {
    abstract val id: String
    abstract val display: CommonDisplay
    abstract val conditions: Conditions

    internal abstract val hasChannelSubscriptions: Boolean
    internal abstract val hasContactSubscriptions: Boolean

    /**
     * Channel subscription preference item.
     */
    data class ChannelSubscription(
        override val id: String,
        val subscriptionId: String,
        override val display: CommonDisplay,
        override val conditions: Conditions
    ) : Item(TYPE_CHANNEL_SUBSCRIPTION) {
        override val hasChannelSubscriptions = true
        override val hasContactSubscriptions = false

        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_SUBSCRIPTION_ID, subscriptionId)
                .build()
    }

    /**
     * Channel subscription preference item.
     */
    data class ContactSubscription(
        override val id: String,
        val subscriptionId: String,
        val scopes: Set<Scope>,
        override val display: CommonDisplay,
        override val conditions: Conditions
    ) : Item(TYPE_CONTACT_SUBSCRIPTION) {
        override val hasChannelSubscriptions = false
        override val hasContactSubscriptions = true

        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_SUBSCRIPTION_ID, subscriptionId)
                .build()
    }

    /**
     * Contact subscription group.
     */
    data class ContactSubscriptionGroup(
        override val id: String,
        val subscriptionId: String,
        val components: List<Component>,
        override val display: CommonDisplay,
        override val conditions: Conditions
    ) : Item(TYPE_CONTACT_SUBSCRIPTION_GROUP) {
        override val hasChannelSubscriptions = false
        override val hasContactSubscriptions = true

        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_SUBSCRIPTION_ID, subscriptionId)
                .put(KEY_COMPONENTS, components.toJson())
                .build()

        data class Component(
            val scopes: Set<Scope>,
            val display: CommonDisplay
        ) {

            fun toJson(): JsonMap =
                jsonMapOf(
                    KEY_SCOPES to JsonValue.wrap(scopes.map(Scope::toJsonValue)),
                    KEY_DISPLAY to display.toJson()
                )

            companion object {
                internal fun parse(json: JsonMap): Component {
                    return Component(
                        scopes = json.opt(KEY_SCOPES).optList().map(Scope::fromJson).toSet(),
                        display = CommonDisplay.parse(json.get(KEY_DISPLAY))
                    )
                }
            }
        }

        private fun List<Component>.toJson(): JsonValue =
            JsonValue.wrap(this.map(Component::toJson))
    }

    /**
     * Alert item with an icon, title, description, and optional button.
     */
    data class Alert(
        override val id: String,
        val iconDisplay: IconDisplay,
        val button: Button?,
        override val conditions: Conditions
    ) : Item(TYPE_ALERT) {
        override val hasChannelSubscriptions = false
        override val hasContactSubscriptions = false

        override val display = CommonDisplay(iconDisplay.name, iconDisplay.description)

        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_DISPLAY, iconDisplay.toJson())
                .put(KEY_BUTTON, button?.toJson())
                .build()
    }

    /**
     * Channel Opt-In Prompt item
     */
    data class AddChannelPrompt(
        val title: String,
        val description: String,
        val footer: String,
        val submitButton: String,
        val cancelButton: String,
        val onSuccess: ActionableMessage,
    )

    data class ActionableMessage(
        val title: String,
        val body: String,
        val button: String
    )

    data class SmsChannelManagementItem(
        val prompt: AddChannelPrompt,
        val emptyChannelPlaceholder: ActionableMessage,
        val senders: List<SMSSender>,
        override val id: String,
        override val conditions: Conditions
    ) : Item(TYPE_SMS_OPTIN) {
        override val hasChannelSubscriptions = false
        override val hasContactSubscriptions = false
        override val display = CommonDisplay(emptyChannelPlaceholder.title, emptyChannelPlaceholder.body)
        override fun toJson(): JsonMap {
            TODO("Not yet implemented")
        }
    }

    data class EmailChannelManagementItem(
        val prompt: AddChannelPrompt,
        val emptyChannelPlaceholder: ActionableMessage,
        val addButton: String,
        override val id: String,
        override val conditions: Conditions,
        val optinTypes: Set<OptinType>
    ) : Item(TYPE_EMAIL_OPTIN) {
        override val hasChannelSubscriptions = false
        override val hasContactSubscriptions = false
        override val display = CommonDisplay(emptyChannelPlaceholder.title, emptyChannelPlaceholder.body)
        override fun toJson(): JsonMap {
            TODO("Not yet implemented")
        }
    }

    data class SMSSender(
        val senderId: String,
        val countryCode: Int = 1,
        val name: String = "US +1"
    )

    enum class OptinType(private val value: String) : JsonSerializable {
        TRANSACTIONAL("transactional"),
        COMMERCIAL("commercial");
        override fun toJsonValue(): JsonValue = JsonValue.wrap(value)

        companion object {
            fun fromJson(jsonValue: JsonValue): OptinType {
                val valueString = jsonValue.optString()
                for (optinType in OptinType.values()) {
                    if (optinType.value.equals(valueString, true)) {
                        return optinType
                    }
                }
                throw JsonException("Invalid optinType: $valueString")
            }
        }
    }

    companion object {
        private const val TYPE_CHANNEL_SUBSCRIPTION = "channel_subscription"
        private const val TYPE_CONTACT_SUBSCRIPTION = "contact_subscription"
        private const val TYPE_CONTACT_SUBSCRIPTION_GROUP = "contact_subscription_group"
        private const val TYPE_ALERT = "alert"
        private const val TYPE_SMS_OPTIN = "sms_optin"
        private const val TYPE_EMAIL_OPTIN = "email_optin"

        private const val KEY_TYPE = "type"
        private const val KEY_ID = "id"
        private const val KEY_DISPLAY = "display"
        private const val KEY_CONDITIONS = "conditions"
        private const val KEY_BUTTON = "button"

        private const val KEY_SUBSCRIPTION_ID = "subscription_id"
        private const val KEY_COMPONENTS = "components"
        private const val KEY_SCOPES = "scopes"

        /**
         * Parses a `JsonMap` into an `Item` subclass, based on the value of the `type` field.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap): Item {
            val id = json.requireField<String>(KEY_ID)

            return when (val type = json.get(KEY_TYPE)?.string) {
                TYPE_CHANNEL_SUBSCRIPTION -> ChannelSubscription(
                    id = id,
                    subscriptionId = json.requireField(KEY_SUBSCRIPTION_ID),
                    display = CommonDisplay.parse(json.get(KEY_DISPLAY)),
                    conditions = Condition.parse(json.get(KEY_CONDITIONS))
                )
                TYPE_CONTACT_SUBSCRIPTION -> ContactSubscription(
                    id = id,
                    subscriptionId = json.requireField(KEY_SUBSCRIPTION_ID),
                    display = CommonDisplay.parse(json.get(KEY_DISPLAY)),
                    scopes = json.opt(KEY_SCOPES).optList().map(Scope::fromJson).toSet(),
                    conditions = Condition.parse(json.get(KEY_CONDITIONS))
                )
                TYPE_CONTACT_SUBSCRIPTION_GROUP -> {
                    val components = json.opt(KEY_COMPONENTS).list?.map {
                        ContactSubscriptionGroup.Component.parse(it.optMap())
                    } ?: emptyList()
                    ContactSubscriptionGroup(
                        id = id,
                        subscriptionId = json.requireField(KEY_SUBSCRIPTION_ID),
                        display = CommonDisplay.parse(json.get(KEY_DISPLAY)),
                        components = components,
                        conditions = Condition.parse(json.get(KEY_CONDITIONS))
                    )
                }
                TYPE_ALERT -> Alert(
                    id = id,
                    iconDisplay = IconDisplay.parse(json.requireField(KEY_DISPLAY)),
                    button = Button.parse(json.optionalField(KEY_BUTTON)),
                    conditions = Condition.parse(json.get(KEY_CONDITIONS))
                )
                /*TYPE_SMS_OPTIN -> SmsOptinPrompt(
                    id = id,
                    title = json.requireField(KEY_TITLE),
                    description = json.requireField(KEY_DESCRIPTION),
                    buttonText = json.requireField(KEY_BUTTON),
                    placeholder = json.optionalField(KEY_PLACEHOLDER),
                    smsConfirmation = json.requireField(KEY_SMS_CONFIRMATION),
                    smsSenders = json.requireField(KEY_SMS_SENDERS),
                    conditions = Condition.parse(json.get(KEY_CONDITIONS)),
                    optinTypes = json.opt(KEY_OPTIN_TYPES).optList().map(OptinType::fromJson).toSet()
                )*/
                else -> throw JsonException("Unknown Preference Center Item type: '$type'")
            }
        }
    }

    internal abstract fun toJson(): JsonMap

    protected fun jsonMapBuilder(): JsonMap.Builder =
        JsonMap.newBuilder()
            .put(KEY_ID, id)
            .put(KEY_TYPE, type)
            .put(KEY_DISPLAY, display.toJson())
            .put(KEY_CONDITIONS, conditions.map(Condition::toJson).toJsonList())
}
