/* Copyright Airship and Contributors */
package com.urbanairship.json

import java.util.Objects
import kotlin.math.floor

/**
 * Describes the structured JSON output an AI evaluation expects from a model.
 *
 * A recursive JSON Schema type node, so the output can be an object, an array, or a bare scalar:
 * ```json
 * { "type": "object", "properties": { "result": { "type": "string", "enum": ["a", "b"] } }, "required": ["result"] }
 * { "type": "array", "items": { "type": "string" } }
 * ```
 * Properties are unordered, and per JSON Schema a property is optional unless listed in
 * `required`. Backend agnostic and in core, so payload-driven features can decode a schema
 * without depending on an AI module.
 *
 * @param type The expected shape of the value.
 * @param description Optional natural-language description, surfaced to the model.
 * @param extensions JSON Schema vendor extensions (`x-*`) carried verbatim as opaque JSON, so
 * new ones round-trip without parser changes. Keys without the prefix, and keys whose value is
 * null, are dropped; [validate] ignores the rest — they are metadata for other consumers.
 */
public class AirshipJsonSchema @JvmOverloads public constructor(
    public val type: ValueType,
    public val description: String? = null,
    extensions: Map<String, JsonValue> = emptyMap()
) : JsonSerializable {

    /** JSON Schema vendor extensions (`x-*`) carried verbatim from the payload. */
    // Null-valued keys are dropped, not kept: JsonMap.Builder omits a null on the way out, so
    // keeping one here would make an extension silently fail to round-trip.
    public val extensions: Map<String, JsonValue> = extensions
        .filterKeys { it.startsWith(EXTENSION_KEY_PREFIX) }
        .filterValues { !it.isNull }

    /** The shape of a schema node. */
    public sealed class ValueType {

        /**
         * A string value.
         *
         * @param choices Permitted values (JSON Schema `enum`), or `null` for any string.
         * Guided generation keeps a model inside the set; [validate] rejects strays from models
         * that can produce them.
         */
        public data class StringType @JvmOverloads public constructor(
            public val choices: List<String>? = null
        ) : ValueType()

        /** A boolean value (`true` or `false`). */
        public data object BooleanType : ValueType()

        /** A whole-number value (a JSON number with no fractional part). */
        public data object IntegerType : ValueType()

        /** A floating-point value. */
        public data object NumberType : ValueType()

        /**
         * A JSON object.
         *
         * @param properties Named properties, or `null` to allow any.
         * @param required Property names that must be present, or `null` to require none. A
         * name here need not appear in [properties] — its presence is still enforced. A list
         * rather than a set so the order round-trips.
         */
        public data class ObjectType @JvmOverloads public constructor(
            public val properties: Map<String, AirshipJsonSchema>? = null,
            public val required: List<String>? = null
        ) : ValueType()

        /**
         * A homogeneous array.
         *
         * @param items The schema every element conforms to. Tuple (positional) `items` is not
         * supported — a model can't guarantee positional output.
         */
        public data class ArrayType public constructor(
            public val items: AirshipJsonSchema
        ) : ValueType()
    }

    /**
     * Validates that [json] conforms to this schema.
     *
     * Undescribed keys are ignored, and optional properties may be absent or null.
     *
     * @param json The JSON to validate.
     * @throws JsonException describing the first violation. Returns normally if valid.
     */
    @Throws(JsonException::class)
    public fun validate(json: JsonValue) {
        validate(json, this, "$")
    }

    override fun toJsonValue(): JsonValue {
        val builder = JsonMap.newBuilder()

        when (val type = type) {
            is ValueType.StringType -> {
                builder.put(TYPE, RAW_STRING)
                type.choices?.let { builder.put(ENUM, JsonList(it.map { choice -> JsonValue.wrap(choice) })) }
            }
            ValueType.BooleanType -> builder.put(TYPE, RAW_BOOLEAN)
            ValueType.IntegerType -> builder.put(TYPE, RAW_INTEGER)
            ValueType.NumberType -> builder.put(TYPE, RAW_NUMBER)
            is ValueType.ObjectType -> {
                builder.put(TYPE, RAW_OBJECT)
                type.properties?.let { properties ->
                    builder.put(PROPERTIES, properties.toJsonMap())
                }
                type.required?.let { builder.put(REQUIRED, JsonList(it.map { name -> JsonValue.wrap(name) })) }
            }
            is ValueType.ArrayType -> {
                builder.put(TYPE, RAW_ARRAY)
                builder.put(ITEMS, type.items)
            }
        }

        description?.let { builder.put(DESCRIPTION, it) }
        extensions.forEach { (key, value) -> builder.put(key, value) }

        return builder.build().toJsonValue()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AirshipJsonSchema) return false
        return type == other.type &&
                description == other.description &&
                extensions == other.extensions
    }

    override fun hashCode(): Int = Objects.hash(type, description, extensions)

    override fun toString(): String = toJsonValue().toString()

    public companion object {

        /** Prefix marking the payload keys captured into [extensions]. */
        public const val EXTENSION_KEY_PREFIX: String = "x-"

        private const val TYPE = "type"
        private const val DESCRIPTION = "description"
        private const val PROPERTIES = "properties"
        private const val REQUIRED = "required"
        private const val ITEMS = "items"
        private const val ENUM = "enum"

        private const val RAW_STRING = "string"
        private const val RAW_BOOLEAN = "boolean"
        private const val RAW_INTEGER = "integer"
        private const val RAW_NUMBER = "number"
        private const val RAW_OBJECT = "object"
        private const val RAW_ARRAY = "array"

        /**
         * Parses a schema from JSON, ignoring unknown keys other than `x-*` extensions.
         *
         * @param value The JSON to parse.
         * @return The parsed schema.
         * @throws JsonException if the JSON is not a valid schema.
         */
        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): AirshipJsonSchema {
            val content = value.requireMap()

            val type = when (val rawType = content.require(TYPE).requireString()) {
                RAW_STRING -> ValueType.StringType(
                    choices = content.present(ENUM)?.requireList()?.map { it.requireString() }
                )
                RAW_BOOLEAN -> ValueType.BooleanType
                RAW_INTEGER -> ValueType.IntegerType
                RAW_NUMBER -> ValueType.NumberType
                RAW_OBJECT -> ValueType.ObjectType(
                    properties = content.present(PROPERTIES)?.requireMap()?.map?.mapValues { fromJson(it.value) },
                    required = content.present(REQUIRED)?.requireList()?.map { it.requireString() }
                )
                RAW_ARRAY -> ValueType.ArrayType(items = fromJson(content.require(ITEMS)))
                else -> throw JsonException("Unknown JSON schema type: $rawType")
            }

            return AirshipJsonSchema(
                type = type,
                description = content.present(DESCRIPTION)?.requireString(),
                extensions = content.map.filterKeys { it.startsWith(EXTENSION_KEY_PREFIX) }
            )
        }

        /**
         * A string value, optionally constrained to a fixed set of [choices].
         *
         * @param choices Permitted values (JSON Schema `enum`), or `null` for any string.
         * @param description Optional description surfaced to the model.
         * @return The schema.
         */
        @JvmStatic
        @JvmOverloads
        public fun string(
            choices: List<String>? = null,
            description: String? = null
        ): AirshipJsonSchema = AirshipJsonSchema(ValueType.StringType(choices), description)

        /**
         * A boolean value (`true` or `false`).
         *
         * @param description Optional description surfaced to the model.
         * @return The schema.
         */
        @JvmStatic
        @JvmOverloads
        public fun boolean(description: String? = null): AirshipJsonSchema =
            AirshipJsonSchema(ValueType.BooleanType, description)

        /**
         * A whole-number value (a JSON number with no fractional part).
         *
         * @param description Optional description surfaced to the model.
         * @return The schema.
         */
        @JvmStatic
        @JvmOverloads
        public fun integer(description: String? = null): AirshipJsonSchema =
            AirshipJsonSchema(ValueType.IntegerType, description)

        /**
         * A floating-point value.
         *
         * @param description Optional description surfaced to the model.
         * @return The schema.
         */
        @JvmStatic
        @JvmOverloads
        public fun number(description: String? = null): AirshipJsonSchema =
            AirshipJsonSchema(ValueType.NumberType, description)

        /**
         * A JSON object with declared [properties].
         *
         * ```
         * AirshipJsonSchema.obj(
         *     properties = mapOf("id" to AirshipJsonSchema.string(), "score" to AirshipJsonSchema.integer()),
         *     required = listOf("id", "score")
         * )
         * ```
         *
         * @param properties The declared properties, or `null` to allow any.
         * @param required Property names the model must always emit, or `null` to require none.
         * @param description Optional description surfaced to the model.
         * @return The schema.
         */
        @JvmStatic
        @JvmOverloads
        public fun obj(
            properties: Map<String, AirshipJsonSchema>? = null,
            required: List<String>? = null,
            description: String? = null
        ): AirshipJsonSchema = AirshipJsonSchema(ValueType.ObjectType(properties, required), description)

        /**
         * A homogeneous array where every element matches [items].
         *
         * @param items The schema every element conforms to.
         * @param description Optional description surfaced to the model.
         * @return The schema.
         */
        @JvmStatic
        @JvmOverloads
        public fun array(
            items: AirshipJsonSchema,
            description: String? = null
        ): AirshipJsonSchema = AirshipJsonSchema(ValueType.ArrayType(items), description)

        /**
         * The value at [key], or `null` when the key is absent or explicitly JSON null.
         *
         * A serializer that writes nulls for absent fields is as good as omitting them, so
         * every optional keyword reads through this. `items` deliberately does not — an array
         * node without it is malformed, not unconstrained.
         */
        private fun JsonMap.present(key: String): JsonValue? =
            this[key]?.takeUnless { it.isNull }

        @Throws(JsonException::class)
        private fun validate(value: JsonValue, schema: AirshipJsonSchema, path: String) {
            when (val type = schema.type) {
                is ValueType.StringType -> {
                    val string = value.string ?: throw typeError(path, RAW_STRING)
                    val choices = type.choices
                    if (choices != null && !choices.contains(string)) {
                        throw JsonException(
                            "Schema validation: '$string' at '$path' is not one of $choices"
                        )
                    }
                }
                ValueType.BooleanType -> {
                    if (!value.isBoolean) throw typeError(path, RAW_BOOLEAN)
                }
                ValueType.IntegerType -> {
                    val number = value.number?.toDouble() ?: throw typeError(path, RAW_INTEGER)
                    if (number != floor(number) || number.isInfinite()) {
                        throw typeError(path, RAW_INTEGER)
                    }
                }
                ValueType.NumberType -> {
                    if (!value.isNumber) throw typeError(path, RAW_NUMBER)
                }
                is ValueType.ObjectType -> {
                    val map = value.map ?: throw typeError(path, RAW_OBJECT)

                    type.required?.forEach { name ->
                        val child = map[name]
                        if (child == null || child.isNull) {
                            throw JsonException(
                                "Schema validation: missing required property '$path.$name'"
                            )
                        }
                    }

                    // Only declared properties are type checked. No declared properties means
                    // the object is unconstrained.
                    type.properties?.forEach { (name, property) ->
                        val child = map[name]
                        if (child != null && !child.isNull) {
                            validate(child, property, "$path.$name")
                        }
                    }
                }
                is ValueType.ArrayType -> {
                    val list = value.list ?: throw typeError(path, RAW_ARRAY)
                    list.forEachIndexed { index, item ->
                        validate(item, type.items, "$path[$index]")
                    }
                }
            }
        }

        private fun typeError(path: String, expected: String): JsonException =
            JsonException("Schema validation: expected $expected at '$path'")
    }
}
