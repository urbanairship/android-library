package com.urbanairship.android.layout.reporting

import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.info.ThomasEmailRegistrationOptions
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.channel.ChannelRegistrationPayload
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

public sealed class FormData<T>(
    internal val type: Type,
) {

    public abstract val identifier: String
    public abstract val value: T?
    public abstract val isValid: Boolean
    public abstract val attributeName: AttributeName?
    public abstract val attributeValue: AttributeValue?
    public abstract val channelRegistration: ThomasChannelRegistration?

    public enum class Type(private val value: String) : JsonSerializable {
        FORM("form"),
        NPS_FORM("nps"),
        TOGGLE("toggle"),
        MULTIPLE_CHOICE("multiple_choice"),
        SINGLE_CHOICE("single_choice"),
        TEXT("text_input"),
        EMAIL("email_input"),
        SCORE("score");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(value)
    }

    internal open val formData: JsonMap
        get() = jsonMapOf(
            KEY_TYPE to type,
            KEY_VALUE to JsonValue.wrapOpt(value)
        )

    public fun jsonValue(): JsonValue? =
        JsonValue.wrapOpt(value).let {
            if (it != JsonValue.NULL) it else null
        }

    public data class Toggle(
        override val identifier: String,
        override val value: Boolean?,
        override val isValid: Boolean,
        override val attributeName: AttributeName? = null,
        override val attributeValue: AttributeValue? = null,
        override val channelRegistration: ThomasChannelRegistration? = null
    ) : FormData<Boolean>(Type.TOGGLE)

    public data class CheckboxController(
        override val identifier: String,
        override val value: Set<JsonValue>?,
        override val isValid: Boolean,
        override val attributeName: AttributeName? = null,
        override val attributeValue: AttributeValue? = null,
        override val channelRegistration: ThomasChannelRegistration? = null
    ) : FormData<Set<JsonValue>>(Type.MULTIPLE_CHOICE)

    public data class RadioInputController(
        override val identifier: String,
        override val value: JsonValue?,
        override val isValid: Boolean,
        override val attributeName: AttributeName? = null,
        override val attributeValue: AttributeValue? = null,
        override val channelRegistration: ThomasChannelRegistration? = null
    ) : FormData<JsonValue>(
        Type.SINGLE_CHOICE,
    )

    public data class TextInput(
        val textInput: FormInputType,
        override val identifier: String,
        override val value: String?,
        override val isValid: Boolean,
        override val attributeName: AttributeName? = null,
        override val attributeValue: AttributeValue? = null,
        override val channelRegistration: ThomasChannelRegistration? = null
    ) : FormData<String>(if (textInput == FormInputType.EMAIL) Type.EMAIL else Type.TEXT)

    public data class Score(
        override val identifier: String,
        override val value: Int?,
        override val isValid: Boolean,
        override val attributeName: AttributeName? = null,
        override val attributeValue: AttributeValue? = null,
        override val channelRegistration: ThomasChannelRegistration? = null
    ) : FormData<Int>(Type.SCORE)

    public sealed class BaseForm(
        type: Type,
        override val identifier: String,
        override val value: Set<FormData<*>>,
        override val isValid: Boolean = value.all { it.isValid },
        override val attributeName: AttributeName? = null,
        override val attributeValue: AttributeValue? = null,
        override val channelRegistration: ThomasChannelRegistration? = null
    ) : FormData<Set<FormData<*>>>(type), JsonSerializable {
        protected abstract val responseType: String?

        protected val childrenJson: JsonSerializable
            get() {
                val builder: JsonMap.Builder = JsonMap.newBuilder()
                for (child in value) {
                    builder.putOpt(child.identifier, child.formData)
                }
                return builder.build()
            }

        override fun toJsonValue(): JsonValue =
            jsonMapOf(identifier to formData).toJsonValue()
    }

    public data class Form(
        override val identifier: String,
        override val responseType: String?,
        val children: Set<FormData<*>>,
    ) : BaseForm(Type.FORM, identifier, children) {

        override val formData: JsonMap
            get() = jsonMapOf(
                KEY_TYPE to type,
                KEY_CHILDREN to childrenJson,
                KEY_RESPONSE_TYPE to responseType
            )
    }

    public data class Nps(
        override val identifier: String,
        private val scoreId: String,
        override val responseType: String?,
        val children: Set<FormData<*>>,
    ) : BaseForm(Type.NPS_FORM, identifier, children) {
        override val formData: JsonMap
            get() = jsonMapOf(
                KEY_TYPE to type,
                KEY_CHILDREN to childrenJson,
                KEY_SCORE_ID to scoreId,
                KEY_RESPONSE_TYPE to responseType
            )
    }

    internal companion object {
        private const val KEY_TYPE: String = "type"
        private const val KEY_VALUE: String = "value"
        private const val KEY_SCORE_ID: String = "score_id"
        private const val KEY_CHILDREN: String = "children"
        private const val KEY_RESPONSE_TYPE: String = "response_type"
    }

    override fun toString(): String {
        return "${formData.toJsonValue()}"
    }
}
