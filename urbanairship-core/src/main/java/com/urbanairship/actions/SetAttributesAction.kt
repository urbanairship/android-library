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
 * Accepts two argument formats:
 *
 * **Dictionary format**
 * ```json
 * {
 *     "channel": {
 *         "set": { "key": "value" },
 *         "remove": ["attribute"]
 *     },
 *     "named_user": {
 *         "set": { "key": "value" },
 *         "remove": ["attribute"]
 *     }
 * }
 * ```
 *
 * **Array format** — each element describes a single operation:
 * ```json
 * [
 *     { "action": "set",    "type": "channel",    "name": "color",   "value": "blue" },
 *     { "action": "remove", "type": "channel",    "name": "color" },
 *     { "action": "set",    "type": "named_user", "name": "score",   "value": 42 },
 *     { "action": "set",    "type": "channel",    "name": "attr#id", "value": { "key": "val", "exp": 1779840000 } }
 * ]
 * ```
 * For JSON object values, `name` must be `"attributeName#instanceId"` with both parts non-empty.
 * The optional `"exp"` key inside the object is a Unix timestamp (seconds) for the expiration date.
 *
 * Accepted situations: all
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
                            when(val number = unwrapped.value) {
                                is Int -> editor.setAttribute(item.name, number)
                                is Long -> editor.setAttribute(item.name, number)
                                is Float -> editor.setAttribute(item.name, number)
                                is Double -> editor.setAttribute(item.name, number)
                            }
                        }
                        is AttributeActionArgs.Value.StringValue -> editor.setAttribute(item.name, unwrapped.value)
                        is AttributeActionArgs.Value.Json -> editor.setAttribute(
                            attribute = unwrapped.attributeName,
                            instanceId = unwrapped.instanceId,
                            expiration = unwrapped.expiration,
                            json = unwrapped.value
                        )
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
            data class Json(
                val attributeName: String,
                val instanceId: String,
                val expiration: Date?,
                val value: JsonMap
            ) : Value()

            override fun toJsonValue(): JsonValue {
                return when (this) {
                    is StringValue -> JsonValue.wrap(value)
                    is NumberValue -> JsonValue.wrap(value)
                    is DateValue -> JsonValue.wrap(value.time)
                    is Json -> {
                        val content = JsonMap.newBuilder()
                            .putAll(value)
                            .putOpt(KEY_EXPIRATION, expiration?.let { it.time / 1000 })
                            .build()
                        content.toJsonValue()
                    }
                }
            }

            companion object {

                private const val KEY_EXPIRATION = "exp"

                @Throws(JsonException::class)
                fun fromJson(value: JsonValue, name: String? = null): Value {
                    return when (val converted = value.value) {
                        is String -> StringValue(converted)
                        is Long -> DateValue(Date(converted * 1000))
                        is Number -> NumberValue(converted)
                        is JsonSerializable -> {
                            val attrName = name
                                ?: throw JsonException("Name is required for JSON object attribute values")
                            val components = attrName.split("#")
                            if (components.size != 2 || components.any { it.isEmpty() }) {
                                throw JsonException("Invalid name format: $attrName")
                            }
                            val data = converted.toJsonValue().requireMap().map.toMutableMap()
                            val expiration = data.remove(KEY_EXPIRATION)?.number
                                ?.let { Date(it.toLong() * 1000) }
                            Json(
                                attributeName = components[0],
                                instanceId = components[1],
                                expiration = expiration,
                                value = data.toJsonMap()
                            )
                        }
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
                        value = Value.fromJson(content.require(KEY_VALUE), name)
                    )

                    Action.REMOVE -> Remove(editor, name)
                }
            }
        }
    }

    /**
     * Default [SetAttributesAction.SetAttributesPredicate] predicate.
     */
    public class SetAttributesPredicate public constructor() : ActionPredicate {

        override fun apply(arguments: ActionArguments): Boolean {
            return Situation.PUSH_RECEIVED != arguments.situation
        }
    }

    public companion object {

        /**
         * Default action names.
         */
        public val DEFAULT_NAMES: Set<String> = setOf("set_attributes_action", "modify_attributes_action", "^a")

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
