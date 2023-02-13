package com.urbanairship.android.layout.reporting

import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.util.jsonMapOf
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

public sealed class FormData<T>(
    internal val type: Type,
    public val identifier: String,
    public val value: T?,
    public val isValid: Boolean,
    public val attributeName: AttributeName? = null,
    public val attributeValue: AttributeValue? = null,
) {
    public enum class Type(private val value: String) : JsonSerializable {
        FORM("form"),
        NPS_FORM("nps"),
        TOGGLE("toggle"),
        MULTIPLE_CHOICE("multiple_choice"),
        SINGLE_CHOICE("single_choice"),
        TEXT("text_input"),
        SCORE("score");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(value)
    }

    protected open val formData: JsonMap
        get() = jsonMapOf(
            KEY_TYPE to type,
            KEY_VALUE to JsonValue.wrapOpt(value)
        )

    public fun jsonValue(): JsonValue? =
        JsonValue.wrapOpt(value).let {
            if (it != JsonValue.NULL) it else null
        }

    public class Toggle(
        identifier: String,
        value: Boolean?,
        isValid: Boolean,
        attributeName: AttributeName? = null,
        attributeValue: AttributeValue? = null,
    ) : FormData<Boolean>(
        Type.TOGGLE, identifier, value, isValid, attributeName, attributeValue
    )

    public class CheckboxController(
        identifier: String,
        value: Set<JsonValue>?,
        isValid: Boolean,
        attributeName: AttributeName? = null,
        attributeValue: AttributeValue? = null,
    ) : FormData<Set<JsonValue>>(
        Type.MULTIPLE_CHOICE, identifier, value, isValid, attributeName, attributeValue
    )

    public class RadioInputController(
        identifier: String,
        value: JsonValue?,
        isValid: Boolean,
        attributeName: AttributeName? = null,
        attributeValue: AttributeValue? = null,
    ) : FormData<JsonValue>(
        Type.SINGLE_CHOICE, identifier, value, isValid, attributeName, attributeValue
    )

    public class TextInput(
        identifier: String,
        value: String?,
        isValid: Boolean,
        attributeName: AttributeName? = null,
        attributeValue: AttributeValue? = null,
    ) : FormData<String>(Type.TEXT, identifier, value, isValid, attributeName, attributeValue)

    public class Score(
        identifier: String,
        value: Int?,
        isValid: Boolean,
        attributeName: AttributeName? = null,
        attributeValue: AttributeValue? = null,
    ) : FormData<Int>(Type.SCORE, identifier, value, isValid, attributeName, attributeValue)

    public sealed class BaseForm(
        type: Type,
        identifier: String,
        protected val responseType: String?,
        internal val children: Collection<FormData<*>>,
        isValid: Boolean = children.all { it.isValid }
    ) : FormData<Collection<FormData<*>>>(
        type,
        identifier,
        children,
        isValid
    ), JsonSerializable {

        protected val childrenJson: JsonSerializable
            get() {
                val builder: JsonMap.Builder = JsonMap.newBuilder()
                for (child in value.orEmpty()) {
                    builder.putOpt(child.identifier, child.formData)
                }
                return builder.build()
            }

        override fun toJsonValue(): JsonValue =
            jsonMapOf(identifier to formData).toJsonValue()
    }

    public class Form(
        identifier: String,
        responseType: String?,
        children: Collection<FormData<*>>
    ) : BaseForm(Type.FORM, identifier, responseType, children) {

        override val formData: JsonMap
            get() = jsonMapOf(
                KEY_TYPE to type,
                KEY_CHILDREN to childrenJson,
                KEY_RESPONSE_TYPE to responseType
            )
    }

    public class Nps(
        identifier: String,
        private val scoreId: String,
        responseType: String?,
        children: Collection<FormData<*>>
    ) : BaseForm(Type.NPS_FORM, identifier, responseType, children) {
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
