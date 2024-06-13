package com.urbanairship.preferencecenter.data

import com.urbanairship.contacts.ChannelType
import com.urbanairship.contacts.Scope
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.json.requireMap
import com.urbanairship.json.toJsonList

/**
 * Preference items.
 */
public sealed class Item(
    private val type: String,
    internal val hasChannelSubscriptions: Boolean,
    internal val hasContactSubscriptions: Boolean,
    internal val hasContactManagement: Boolean
) {
    public abstract val id: String
    public abstract val display: CommonDisplay
    public abstract val conditions: Conditions

    /**
     * Channel subscription preference item.
     */
    public data class ChannelSubscription(
        override val id: String,
        val subscriptionId: String,
        override val display: CommonDisplay,
        override val conditions: Conditions
    ) : Item(
        type = TYPE_CHANNEL_SUBSCRIPTION,
        hasChannelSubscriptions = true,
        hasContactSubscriptions = false,
        hasContactManagement = false,
    ) {
        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_SUBSCRIPTION_ID, subscriptionId)
                .build()
    }

    /**
     * Channel subscription preference item.
     */
    public data class ContactSubscription(
        override val id: String,
        val subscriptionId: String,
        val scopes: Set<Scope>,
        override val display: CommonDisplay,
        override val conditions: Conditions
    ) : Item(
        type = TYPE_CONTACT_SUBSCRIPTION,
        hasChannelSubscriptions = false,
        hasContactSubscriptions = true,
        hasContactManagement = false,
    ) {

        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_SUBSCRIPTION_ID, subscriptionId)
                .build()
    }

    /**
     * Contact subscription group.
     */
    public data class ContactSubscriptionGroup(
        override val id: String,
        val subscriptionId: String,
        val components: List<Component>,
        override val display: CommonDisplay,
        override val conditions: Conditions
    ) : Item(
        type = TYPE_CONTACT_SUBSCRIPTION_GROUP,
        hasChannelSubscriptions = false,
        hasContactSubscriptions = true,
        hasContactManagement = false,
    ) {
        @Throws(JsonException::class)
        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_SUBSCRIPTION_ID, subscriptionId)
                .put(KEY_COMPONENTS, components.toJson())
                .build()

        public data class Component(
            val scopes: Set<Scope>,
            val display: CommonDisplay
        ) {

            @Throws(JsonException::class)
            internal fun toJson(): JsonMap =
                jsonMapOf(
                    KEY_SCOPES to JsonValue.wrap(scopes.map(Scope::toJsonValue)),
                    KEY_DISPLAY to display.toJson()
                )

            internal companion object {
                @Throws(JsonException::class)
                internal fun parse(json: JsonMap): Component {
                    return Component(
                        scopes = json.opt(KEY_SCOPES).optList().map(Scope::fromJson).toSet(),
                        display = CommonDisplay.parse(json.get(KEY_DISPLAY))
                    )
                }
            }
        }

        @Throws(JsonException::class)
        private fun List<Component>.toJson(): JsonValue =
            JsonValue.wrap(this.map(Component::toJson))
    }

    /**
     * Alert item with an icon, title, description, and optional button.
     */
    public data class Alert(
        override val id: String,
        val iconDisplay: IconDisplay,
        val button: Button?,
        override val conditions: Conditions
    ) : Item(
        type = TYPE_ALERT,
        hasChannelSubscriptions = false,
        hasContactSubscriptions = false,
        hasContactManagement = false,
    ) {
        override val display: CommonDisplay = CommonDisplay(iconDisplay.name, iconDisplay.description)

        @Throws(JsonException::class)
        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_DISPLAY, iconDisplay.toJson())
                .put(KEY_BUTTON, button?.toJson())
                .build()
    }

    public data class ContactManagement(
        override val id: String,
        val platform: Platform,
        override val display: CommonDisplay,
        val addPrompt: AddPrompt,
        val removePrompt: RemovePrompt,
        val emptyLabel: String?,
        val registrationOptions: RegistrationOptions,
        override val conditions: Conditions,
    ) : Item(
        type = TYPE_CONTACT_MANAGEMENT,
        hasChannelSubscriptions = false,
        hasContactSubscriptions = false,
        hasContactManagement = true,
    ) {
        @Throws(JsonException::class)
        override fun toJson(): JsonMap =
            jsonMapBuilder()
                .put(KEY_PLATFORM, platform.toJson())
                .put(KEY_ADD, addPrompt.toJson())
                .put(KEY_REMOVE, removePrompt.toJson())
                .put(KEY_EMPTY_LABEL, emptyLabel)
                .put(KEY_REGISTRATION_OPTIONS, registrationOptions.toJson())
                .build()

        internal companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): ContactManagement {
                return ContactManagement(
                    id = json.requireField(KEY_ID),
                    platform = Platform.fromJson(json.requireField(KEY_PLATFORM)),
                    display = CommonDisplay.parse(json.requireField<JsonMap>(KEY_DISPLAY)),
                    addPrompt = AddPrompt.fromJson(json.requireField(KEY_ADD)),
                    removePrompt = RemovePrompt.fromJson(json.requireField(KEY_REMOVE)),
                    emptyLabel = json.optionalField(KEY_EMPTY_LABEL),
                    registrationOptions = RegistrationOptions.fromJson(json.requireField(KEY_REGISTRATION_OPTIONS)),
                    conditions = Condition.parse(json.requireField<JsonValue>(KEY_CONDITIONS))
                )
            }
        }

        public enum class Platform(public val jsonValue: String) {
            SMS("sms"),
            EMAIL("email");

            public fun toJson(): JsonValue = JsonValue.wrap(jsonValue)

            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(jsonValue: JsonValue): Platform {
                    val valueString = jsonValue.optString()
                    for (platform in entries) {
                        if (platform.jsonValue.equals(valueString, true)) {
                            return platform
                        }
                    }
                    throw JsonException("Invalid platform: $valueString")
                }
            }

            internal fun toChannelType(): ChannelType = when (this) {
                SMS -> ChannelType.SMS
                EMAIL -> ChannelType.EMAIL
            }
        }

        public data class AddPrompt(
            val prompt: AddChannelPrompt,
            val button: LabeledButton
        ) {
            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): AddPrompt {
                    return AddPrompt(
                        prompt = AddChannelPrompt.fromJson(json.requireField(KEY_VIEW)),
                        button = LabeledButton.fromJson(json.requireField(KEY_BUTTON))
                    )
                }
            }

            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_VIEW to prompt.toJson(),
                KEY_BUTTON to button.toJson()
            )
        }

        public data class RemovePrompt(
            val prompt: RemoveChannelPrompt,
            val button: IconButton
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_VIEW to prompt.toJson(),
                KEY_BUTTON to button.toJson()
            )

            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): RemovePrompt {
                    return RemovePrompt(
                        prompt = RemoveChannelPrompt.fromJson(json.requireField(KEY_VIEW)),
                        button = IconButton.fromJson(json.requireField(KEY_BUTTON))
                    )
                }
            }
        }

        public data class AddChannelPrompt(
            val type: String,
            val display: PromptDisplay,
            val submitButton: LabeledButton,
            val cancelButton: LabeledButton?,
            val onSubmit: ActionableMessage?,
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_TYPE to type,
                KEY_DISPLAY to display.toJson(),
                KEY_SUBMIT_BUTTON to submitButton.toJson(),
                KEY_CANCEL_BUTTON to cancelButton?.toJson(),
                KEY_ON_SUBMIT to onSubmit?.toJson(),
            )

            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): AddChannelPrompt {
                    return AddChannelPrompt(
                        type = json.requireField(KEY_TYPE),
                        display = PromptDisplay.fromJson(json.requireField(KEY_DISPLAY)),
                        submitButton = json.requireMap(KEY_SUBMIT_BUTTON).let { LabeledButton.fromJson(it) },
                        cancelButton = json.optionalMap(KEY_CANCEL_BUTTON)?.let { LabeledButton.fromJson(it) },
                        onSubmit = json.optionalMap(KEY_ON_SUBMIT)?.let { ActionableMessage.fromJson(it) },
                    )
                }
            }
        }

        public data class RemoveChannelPrompt(
            val type: String,
            val display: PromptDisplay,
            val submitButton: LabeledButton,
            val cancelButton: LabeledButton?,
            val onSubmit: ActionableMessage?,
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_TYPE to type,
                KEY_DISPLAY to display.toJson(),
                KEY_SUBMIT_BUTTON to submitButton.toJson(),
                KEY_CANCEL_BUTTON to cancelButton?.toJson(),
                KEY_ON_SUBMIT to onSubmit?.toJson(),
            )

            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): RemoveChannelPrompt {
                    return RemoveChannelPrompt(
                        type = json.requireField(KEY_TYPE),
                        display = PromptDisplay.fromJson(json.requireField(KEY_DISPLAY)),
                        submitButton = json.requireMap(KEY_SUBMIT_BUTTON).let { LabeledButton.fromJson(it) },
                        cancelButton = json.optionalMap(KEY_CANCEL_BUTTON)?.let { LabeledButton.fromJson(it) },
                        onSubmit = json.optionalMap(KEY_ON_SUBMIT)?.let { ActionableMessage.fromJson(it) },
                    )
                }
            }
        }

        public data class FormattedText(
            val text: String,
            val type: FormatType
        ) {
            public enum class FormatType(public val value: String) {
                PLAIN("string"),
                MARKDOWN("markdown"),
                UNKNOWN("unknown");

               internal companion object {
                    fun from(value: String): FormatType =
                        entries.firstOrNull { it.value.equals(value, true) } ?: UNKNOWN
               }
            }

            internal val isMarkdown = type == FormatType.MARKDOWN

            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_DESCRIPTION to text,
                KEY_TYPE to type.value
            )

            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): FormattedText {
                    return FormattedText(
                        text = json.requireField(KEY_DESCRIPTION),
                        type = FormatType.from(json.requireField<String>(KEY_TYPE))
                    )
                }
            }
        }

        public data class PromptDisplay(
            val title: String,
            val description: String?,
            val footer: FormattedText?,
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_TITLE to title,
                KEY_DESCRIPTION to description,
                KEY_FOOTER to footer?.toJson()
            )

            internal companion object {
                private const val KEY_TITLE = "title"
                private const val KEY_FOOTER = "footer"

                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): PromptDisplay {
                    return PromptDisplay(
                        title = json.requireField(KEY_TITLE),
                        description = json.optionalField(KEY_DESCRIPTION),
                        footer = json.optionalMap(KEY_FOOTER)?.let { FormattedText.fromJson(it) },
                    )
                }
            }
        }

        public data class ActionableMessage(
            val title: String,
            val description: String?,
            val button: LabeledButton,
            val contentDescription: String?
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_NAME to title,
                KEY_DESCRIPTION to description,
                KEY_BUTTON to button.toJson(),
                KEY_CONTENT_DESCRIPTION to contentDescription
            )

            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): ActionableMessage {
                    return ActionableMessage(
                        title = json.requireField(KEY_NAME),
                        description = json.optionalField(KEY_DESCRIPTION),
                        button = LabeledButton.fromJson(json.requireField(KEY_BUTTON)),
                        contentDescription = json.optionalField(KEY_CONTENT_DESCRIPTION)
                    )
                }
            }
        }

        public data class IconButton(
            val contentDescription: String?
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_CONTENT_DESCRIPTION to contentDescription
            )

            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): IconButton {
                    return IconButton(
                        contentDescription = json.optionalField(KEY_CONTENT_DESCRIPTION)
                    )
                }
            }
        }

        public data class LabeledButton(
            val text: String,
            val contentDescription: String?
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_TEXT to text,
                KEY_CONTENT_DESCRIPTION to contentDescription
            )

            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): LabeledButton {
                    return LabeledButton(
                        text = json.requireField(KEY_TEXT),
                        contentDescription = json.optionalField(KEY_CONTENT_DESCRIPTION)
                    )
                }
            }
        }

        public data class ErrorMessages(
            /** Invalid SMS or email address. */
            public val invalidMessage: String,
            /** Fallback error. */
            public val defaultMessage: String
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_INVALID to invalidMessage,
                KEY_DEFAULT to defaultMessage
            )

            internal companion object {
                private const val KEY_INVALID = "invalid"
                private const val KEY_DEFAULT = "default"

                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): ErrorMessages {
                    return ErrorMessages(
                        invalidMessage = json.requireField(KEY_INVALID),
                        defaultMessage = json.requireField(KEY_DEFAULT)
                    )
                }
            }
        }

        public data class ResendOptions(
            /** Seconds to wait before refreshing channel opt-in state. */
            val interval: Int,
            val message: String,
            val button: LabeledButton,
            val onSuccess: ActionableMessage?
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_INTERVAL to interval,
                KEY_MESSAGE to message,
                KEY_BUTTON to button.toJson(),
                KEY_ON_SUBMIT to onSuccess?.toJson()
            )

            internal companion object {
                private const val KEY_INTERVAL = "interval"
                private const val KEY_MESSAGE = "message"

                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): ResendOptions {
                    return ResendOptions(
                        interval = json.requireField(KEY_INTERVAL),
                        message = json.requireField(KEY_MESSAGE),
                        button = LabeledButton.fromJson(json.requireField(KEY_BUTTON)),
                        onSuccess = json.optionalMap(KEY_ON_SUBMIT)?.let { ActionableMessage.fromJson(it) }
                    )
                }
            }
        }

        public sealed class RegistrationOptions(
            public val type: String
        ) {
            public abstract val resendOptions: ResendOptions
            public abstract val errorMessages: ErrorMessages

            @Throws(JsonException::class)
            public abstract fun toJson(): JsonMap

            public data class Sms(
                val senders: List<SmsSenderInfo>,
                val countryLabel: String,
                val phoneLabel: String,
                override val resendOptions: ResendOptions,
                override val errorMessages: ErrorMessages,
            ): RegistrationOptions("sms") {

                @Throws(JsonException::class)
                public override fun toJson(): JsonMap = jsonMapOf(
                    KEY_SENDERS to senders.map { it.toJson() },
                    KEY_COUNTRY_LABEL to countryLabel,
                    KEY_PHONE_LABEL to phoneLabel,
                    KEY_RESEND to resendOptions.toJson(),
                    KEY_ERROR_MESSAGES to errorMessages.toJson()
                )

                internal companion object {
                    private const val KEY_SENDERS = "senders"
                    private const val KEY_COUNTRY_LABEL = "country_label"
                    private const val KEY_PHONE_LABEL = "msisdn_label"

                    @Throws(JsonException::class)
                    fun fromJson(json: JsonMap): Sms {
                        return Sms(
                            senders = json.requireField<JsonList>(KEY_SENDERS).map { SmsSenderInfo.fromJson(it.requireMap()) },
                            countryLabel = json.requireField(KEY_COUNTRY_LABEL),
                            phoneLabel = json.requireField(KEY_PHONE_LABEL),
                            resendOptions = ResendOptions.fromJson(json.requireField(KEY_RESEND)),
                            errorMessages = json.requireMap(KEY_ERROR_MESSAGES).let { ErrorMessages.fromJson(it) }
                        )
                    }
                }
            }

            public data class Email(
                public val placeholder: String?,
                public val addressLabel: String,
                /** A key-value mapping of properties that will be passed to the double opt-in registration endpoint. */
                public val properties: JsonMap?,
                public override val resendOptions: ResendOptions,
                public override val errorMessages: ErrorMessages,
            ): RegistrationOptions("email") {

                @Throws(JsonException::class)
                public override fun toJson(): JsonMap = jsonMapOf(
                    KEY_PLACEHOLDER to placeholder,
                    KEY_ADDRESS_LABEL to addressLabel,
                    KEY_PROPERTIES to properties,
                    KEY_RESEND to resendOptions.toJson(),
                    KEY_ERROR_MESSAGES to errorMessages.toJson()
                )

                internal companion object {
                    private const val KEY_ADDRESS_LABEL = "address_label"
                    private const val KEY_PROPERTIES = "properties"

                    @Throws(JsonException::class)
                    fun fromJson(json: JsonMap): Email {
                        return Email(
                            placeholder = json.optionalField(KEY_PLACEHOLDER),
                            addressLabel = json.requireField(KEY_ADDRESS_LABEL),
                            properties = json.optionalField(KEY_PROPERTIES),
                            resendOptions = ResendOptions.fromJson(json.requireField(KEY_RESEND)),
                            errorMessages = json.requireMap(KEY_ERROR_MESSAGES).let { ErrorMessages.fromJson(it) }
                        )
                    }
                }
            }

            internal companion object {
                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): RegistrationOptions {
                    return when (val type = json.requireField<String>(KEY_TYPE)) {
                        Platform.SMS.jsonValue -> Sms.fromJson(json)
                        Platform.EMAIL.jsonValue -> Email.fromJson(json)
                        else -> throw JsonException("Invalid registration type: $type")
                    }
                }
            }
        }

        public data class SmsSenderInfo(
            val senderId: String,
            val placeholderText: String,
            val dialingCode: String,
            val displayName: String,
        ) {
            @Throws(JsonException::class)
            public fun toJson(): JsonMap = jsonMapOf(
                KEY_SENDER_ID to senderId,
                KEY_PLACEHOLDER to placeholderText,
                KEY_COUNTRY_CODE to dialingCode,
                KEY_DISPLAY_NAME to displayName
            )

            internal companion object {
                private const val KEY_SENDER_ID = "sender_id"
                private const val KEY_COUNTRY_CODE = "country_code"
                private const val KEY_DISPLAY_NAME = "display_name"

                @Throws(JsonException::class)
                fun fromJson(json: JsonMap): SmsSenderInfo {
                    return SmsSenderInfo(
                        senderId = json.requireField(KEY_SENDER_ID),
                        placeholderText = json.requireField(KEY_PLACEHOLDER),
                        dialingCode = json.requireField(KEY_COUNTRY_CODE),
                        displayName = json.requireField(KEY_DISPLAY_NAME)
                    )
                }
            }
        }
    }

    internal companion object {
        private const val TYPE_CHANNEL_SUBSCRIPTION = "channel_subscription"
        private const val TYPE_CONTACT_SUBSCRIPTION = "contact_subscription"
        private const val TYPE_CONTACT_SUBSCRIPTION_GROUP = "contact_subscription_group"
        private const val TYPE_ALERT = "alert"
        private const val TYPE_CONTACT_MANAGEMENT = "contact_management"

        private const val KEY_TYPE = "type"
        private const val KEY_ID = "id"
        private const val KEY_DISPLAY = "display"
        private const val KEY_CONDITIONS = "conditions"
        private const val KEY_BUTTON = "button"

        private const val KEY_SUBSCRIPTION_ID = "subscription_id"
        private const val KEY_COMPONENTS = "components"
        private const val KEY_SCOPES = "scopes"

        private const val KEY_PLATFORM = "platform"
        private const val KEY_EMPTY_LABEL = "empty_message"
        private const val KEY_ADD = "add"
        private const val KEY_REMOVE = "remove"
        private const val KEY_REGISTRATION_OPTIONS = "registration_options"

        private const val KEY_VIEW = "view"
        private const val KEY_ON_SUBMIT = "on_submit"
        private const val KEY_CANCEL_BUTTON = "cancel_button"
        private const val KEY_SUBMIT_BUTTON = "submit_button"

        private const val KEY_NAME = "name"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_CONTENT_DESCRIPTION = "content_description"
        private const val KEY_TEXT = "text"

        private const val KEY_RESEND = "resend"
        private const val KEY_ERROR_MESSAGES = "error_messages"
        private const val KEY_PLACEHOLDER = "placeholder_text"

        /**
         * Parses a `JsonMap` into an `Item` subclass, based on the value of the `type` field.
         *
         * @throws JsonException
         */
        @Throws(JsonException::class)
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
                TYPE_CONTACT_MANAGEMENT -> ContactManagement(
                    id = id,
                    platform = ContactManagement.Platform.fromJson(json.requireField(KEY_PLATFORM)),
                    display = CommonDisplay.parse(json.get(KEY_DISPLAY)),
                    emptyLabel = json.optionalField(KEY_EMPTY_LABEL),
                    conditions = Condition.parse(json.get(KEY_CONDITIONS)),
                    addPrompt = ContactManagement.AddPrompt.fromJson(json.requireField(KEY_ADD)),
                    removePrompt = ContactManagement.RemovePrompt.fromJson(json.requireField(KEY_REMOVE)),
                    registrationOptions = ContactManagement.RegistrationOptions.fromJson(json.requireField(KEY_REGISTRATION_OPTIONS))
                )
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
