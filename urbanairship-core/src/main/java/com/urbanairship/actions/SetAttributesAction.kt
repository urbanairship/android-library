/* Copyright Airship and Contributors */
package com.urbanairship.actions

import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.contacts.Contact
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.json.toJsonMap
import java.util.Date

/**
 * An action that sets attributes.
 *
 *
 * Accepted situations: all
 *
 *
 * Accepted argument value types: A JSON payload for setting or removing attributes. An example JSON payload:
 * ```
 * {
 *  "channel": {
 *      set: {"key_1": value_1, "key_2": value_2},
 *      remove: ["attribute_1", "attribute_2", "attribute_3"]
 *  },
 *  "named_user": {
 *      set: {"key_4": value_4, "key_5": value_5},
 *      remove: ["attribute_4", "attribute_5", "attribute_6"]
 *  }
 * ```
 */
public class SetAttributesAction public constructor(
    private val channelProvider: () -> AirshipChannel = { Airship.channel },
    private val contactProvider: () -> Contact = { Airship.contact }
) : Action() {

    override fun perform(arguments: ActionArguments): ActionResult {
        val actions = try {
            parseActions(arguments.value.toJsonValue())
        } catch (ex: kotlin.Exception) {
            UALog.w(ex) { "Failed to parse actions" }
            return newEmptyResult()
        }

        val channelEditor = channelProvider().editAttributes()
        val contactEditor = contactProvider().editAttributes()

        val targets = actions.map { it.editor }.toSet()

        for (item in actions) {
            val editor = when (item.editor) {
                AttributeActionArgs.Editor.CHANNEL -> channelEditor
                AttributeActionArgs.Editor.CONTACT -> contactEditor
            }

            when(item) {
                is AttributeActionArgs.Remove -> editor.removeAttribute(item.name)
                is AttributeActionArgs.Set -> {
                    when(val unwrapped = item.value) {
                        is AttributeActionArgs.Value.DateValue -> editor.setAttribute(item.name, unwrapped.value)
                        is AttributeActionArgs.Value.NumberValue -> {
                            val number = unwrapped.value
                            when(number) {
                                is Int -> editor.setAttribute(item.name, number)
                                is Long -> editor.setAttribute(item.name, number)
                                is Float -> editor.setAttribute(item.name, number)
                                is Double -> editor.setAttribute(item.name, number)
                            }
                        }
                        is AttributeActionArgs.Value.StringValue -> editor.setAttribute(item.name, unwrapped.value)
                        is AttributeActionArgs.Value.Json -> {
                            val expirable = unwrapped.value
                            editor.setAttribute(
                                attribute = expirable.name,
                                instanceId = expirable.instanceId,
                                expiration = expirable.expiration,
                                json = expirable.value)
                        }
                    }
                }
            }
        }

        if (targets.contains(AttributeActionArgs.Editor.CHANNEL)) {
            channelEditor.apply()
        }

        if (targets.contains(AttributeActionArgs.Editor.CONTACT)) {
            contactEditor.apply()
        }

        return newEmptyResult()
    }

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        try {
            val actions = parseActions(arguments.value.toJsonValue())
            return actions.isNotEmpty()
        } catch (_: Exception) {
            return false
        }
    }

    @Throws(JsonException::class, IllegalArgumentException::class)
    private fun parseActions(value: JsonValue): List<AttributeActionArgs> {
        if (value.isNull) {
            throw IllegalArgumentException("Null is not allowed")
        }

        if (value.isJsonList) {
            return value.requireList().list.map(AttributeActionArgs::fromJson)
        }

        val map = value.map?.map ?: throw IllegalArgumentException("json map is expected")

        val convertToAttribute: (AttributeActionArgs.Editor, JsonMap?) -> List<AttributeActionArgs> = convertToAttribute@ { editor, source ->
            if (source == null) {
                return@convertToAttribute emptyList()
            }

            val sets = source.map[SET_KEY]
                ?.requireMap()
                ?.map { (key, value) ->
                    AttributeActionArgs.Set(
                        editor = editor,
                        name = key,
                        value = AttributeActionArgs.Value.fromMapValue(value.value)
                    )
                }
                ?: emptyList()

            val removes = source.map[REMOVE_KEY]
                ?.requireList()
                ?.list
                ?.map { AttributeActionArgs.Remove(editor, it.requireString()) }
                ?: emptyList()

            sets + removes
        }

        return convertToAttribute(AttributeActionArgs.Editor.CHANNEL, map[CHANNEL_KEY]?.requireMap()) +
                convertToAttribute(AttributeActionArgs.Editor.CONTACT, map[NAMED_USER_KEY]?.requireMap())
    }

    private sealed class AttributeActionArgs(
        val action: Action,
        val editor: Editor,
        val name: String
    ): JsonSerializable {

        class Set(
            editor: Editor, name: String, val value: Value
        ) : AttributeActionArgs(Action.SET, editor, name)

        class Remove(
            editor: Editor, name: String
        ) : AttributeActionArgs(Action.REMOVE, editor, name)

        override fun toJsonValue(): JsonValue {
            val builder = JsonMap.newBuilder().put(KEY_ACTION, action).put(KEY_TARGET, editor)
                .put(KEY_NAME, name)

            when (this) {
                is Set -> builder.put(KEY_VALUE, value)
                else -> {}
            }

            return builder.build().toJsonValue()
        }

        enum class Action(val jsonValue: String) : JsonSerializable { SET("set"), REMOVE("remove");

            override fun toJsonValue(): JsonValue = JsonValue.wrap(jsonValue)

            companion object {

                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): Action {
                    val content = value.requireString()
                    return entries.firstOrNull { it.jsonValue == content }
                        ?: throw JsonException("invalid value $value")
                }
            }
        }

        enum class Editor(val jsonValue: String) :
            JsonSerializable {

            CHANNEL("channel"), CONTACT("contact");

            override fun toJsonValue(): JsonValue = JsonValue.wrap(jsonValue)

            companion object {

                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): Editor {
                    val content = value.requireString()
                    return entries.firstOrNull { it.jsonValue == content }
                        ?: throw JsonException("invalid value $value")
                }
            }
        }

        sealed class Value() : JsonSerializable { data class StringValue(val value: String) :
            Value()

            data class NumberValue(val value: Number) : Value()
            data class DateValue(val value: Date) : Value()
            data class Json(val value: ExpirableValue) : Value()

            override fun toJsonValue(): JsonValue {
                return when (this) {
                    is StringValue -> JsonValue.wrap(value)
                    is NumberValue -> JsonValue.wrap(value)
                    is DateValue -> JsonValue.wrap(value.time)
                    is Json -> value.toJsonValue()
                }
            }

            class ExpirableValue(
                val name: String, val instanceId: String, val expiration: Date?, val value: JsonMap
            ) : JsonSerializable {

                companion object {

                    private const val KEY_EXPIRATION = "exp"

                    @Throws(JsonException::class)
                    fun fromJson(value: JsonValue): ExpirableValue {
                        val content = value.requireMap()
                        if (content.size() != 1) {
                            throw JsonException("Only one entry in $value is allowed")
                        }

                        val key = content.keySet().firstOrNull()
                            ?: throw JsonException("Empty input data")
                        if (!key.contains("#")) {
                            throw JsonException("Invalid key format: $key")
                        }

                        val data = content.values().firstOrNull()?.requireMap()?.map?.toMutableMap()
                            ?: throw JsonException("Invalid value format: $content")

                        val components = key.split("#")
                        if (components.size != 2 || components.any { it.isEmpty() }) {
                            throw JsonException("Invalid key format: $key")
                        }

                        return ExpirableValue(
                            name = components[0],
                            instanceId = components[1],
                            expiration = convertToData(data.remove(KEY_EXPIRATION)),
                            value = data.toJsonMap()
                        )
                    }

                    fun convertToData(value: JsonValue?): Date? {
                        val number = value?.number ?: return null
                        return Date(number.toLong() * 1000)
                    }
                }

                override fun toJsonValue(): JsonValue {
                    val content =
                        JsonMap.newBuilder().putAll(value).putOpt(KEY_EXPIRATION, expiration?.time)
                            .build()

                    return jsonMapOf("$name#$value" to content).toJsonValue()
                }
            }

            companion object {

                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): Value {
                    return when (val converted = value.value) {
                        is String -> StringValue(converted)
                        is Long -> DateValue(Date(converted * 1000))
                        is Number -> NumberValue(converted)
                        is JsonSerializable -> Json(ExpirableValue.fromJson(converted.toJsonValue()))
                        else -> throw JsonException("Unsupported value type: $converted")
                    }
                }

                @Throws(IllegalArgumentException::class)
                fun fromMapValue(value: Any?): Value {
                    return when (value) {
                        is String -> StringValue(value)
                        is Number -> NumberValue(value)
                        is Date -> DateValue(value)
                        else -> throw IllegalArgumentException("Unsupported value type: $value")
                    }
                }
            }
        }

        companion object {

            private const val KEY_ACTION = "action"
            private const val KEY_TARGET = "type"
            private const val KEY_NAME = "name"
            private const val KEY_VALUE = "value"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): AttributeActionArgs {
                val content = value.requireMap()
                val name: String = content.requireField(KEY_NAME)
                val editor = Editor.fromJson(content.require(KEY_TARGET))

                return when (Action.fromJson(content.require(KEY_ACTION))) {
                    Action.SET -> Set(
                        editor = editor,
                        name = name,
                        value = Value.fromJson(content.require(KEY_VALUE))
                    )

                    Action.REMOVE -> Remove(editor, name)
                }
            }
        }
    }

    /**
     * Default [SetAttributesAction.SetAttributesPredicate] predicate.
     */
    public class SetAttributesPredicate public constructor() : ActionRegistry.Predicate {

        override fun apply(arguments: ActionArguments): Boolean {
            return Situation.PUSH_RECEIVED != arguments.situation
        }
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "set_attributes_action"
        private const val DEFAULT_REGISTRY_NAME_IOS: String = "modify_attributes_action"

        /**
         * Default registry short name
         */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^a"

        /**
         * JSON key for channel attributes changes.
         */
        private const val CHANNEL_KEY = "channel"

        /**
         * JSON key for named user attributes changes.
         */
        private const val NAMED_USER_KEY = "named_user"

        /**
         * JSON key for setting attributes.
         */
        private const val SET_KEY = "set"

        /**
         * JSON key for removing attributes.
         */
        private const val REMOVE_KEY = "remove"
    }
}
