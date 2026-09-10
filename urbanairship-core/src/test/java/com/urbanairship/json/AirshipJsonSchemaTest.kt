/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.AirshipJsonSchema.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class JsonSchemaTest {

    /** A schema exercising every value type, including a nested object and an array. */
    private val schema = AirshipJsonSchema.obj(
        properties = mapOf(
            "allow" to AirshipJsonSchema.boolean(),
            "reason" to AirshipJsonSchema.string(),
            "user" to AirshipJsonSchema.obj(
                properties = mapOf(
                    "age" to AirshipJsonSchema.integer(),
                    "tags" to AirshipJsonSchema.array(items = AirshipJsonSchema.string())
                ),
                required = listOf("age")
            )
        ),
        required = listOf("allow")
    )

    private val enumSchema = AirshipJsonSchema.obj(
        properties = mapOf("result" to AirshipJsonSchema.string(choices = listOf("shipping", "quality", "praise"))),
        required = listOf("result")
    )

    // MARK: validate — happy paths

    @Test
    public fun testValidatePassesForConformingObject() {
        schema.validate(
            jsonMapOf(
                "allow" to true,
                "reason" to "relevant",
                "user" to jsonMapOf("age" to 30, "tags" to jsonListOf("a", "b"))
            ).toJsonValue()
        )
    }

    @Test
    public fun testValidateAllowsAbsentOptionalFields() {
        schema.validate(jsonMapOf("allow" to false).toJsonValue())
    }

    @Test
    public fun testValidateIgnoresExtraKeys() {
        schema.validate(jsonMapOf("allow" to true, "unexpected" to "ignored").toJsonValue())
    }

    @Test
    public fun testValidateAcceptsWholeNumberForInteger() {
        schema.validate(
            jsonMapOf("allow" to true, "user" to jsonMapOf("age" to 42.0)).toJsonValue()
        )
    }

    // MARK: validate — failures

    @Test
    public fun testValidateThrowsForMissingRequiredField() {
        assertThrows(JsonException::class.java) {
            schema.validate(jsonMapOf("reason" to "no allow key").toJsonValue())
        }
    }

    @Test
    public fun testValidateThrowsForMissingRequiredKeyNotInProperties() {
        // `required` may name a key with no entry in `properties` — its presence must
        // still be enforced.
        val schema = AirshipJsonSchema.obj(
            properties = mapOf("known" to AirshipJsonSchema.string()),
            required = listOf("mustExist")
        )

        assertThrows(JsonException::class.java) {
            schema.validate(jsonMapOf("known" to "here").toJsonValue())
        }

        schema.validate(jsonMapOf("known" to "here", "mustExist" to "present").toJsonValue())
    }

    @Test
    public fun testValidateThrowsForWrongScalarType() {
        assertThrows(JsonException::class.java) {
            schema.validate(jsonMapOf("allow" to "not a bool").toJsonValue())
        }
    }

    @Test
    public fun testValidateThrowsForNonObjectRoot() {
        assertThrows(JsonException::class.java) {
            schema.validate(JsonValue.wrap("not an object"))
        }
    }

    @Test
    public fun testValidateThrowsForBadNestedObjectField() {
        assertThrows(JsonException::class.java) {
            schema.validate(
                jsonMapOf("allow" to true, "user" to jsonMapOf("age" to "thirty")).toJsonValue()
            )
        }
    }

    @Test
    public fun testValidateThrowsForBadArrayElement() {
        assertThrows(JsonException::class.java) {
            schema.validate(
                jsonMapOf(
                    "allow" to true,
                    "user" to jsonMapOf("age" to 30, "tags" to jsonListOf("ok", 5))
                ).toJsonValue()
            )
        }
    }

    @Test
    public fun testValidateThrowsForFractionalInteger() {
        assertThrows(JsonException::class.java) {
            schema.validate(
                jsonMapOf("allow" to true, "user" to jsonMapOf("age" to 3.5)).toJsonValue()
            )
        }
    }

    // MARK: string enum

    @Test
    public fun testValidateAcceptsEnumMember() {
        enumSchema.validate(jsonMapOf("result" to "quality").toJsonValue())
    }

    @Test
    public fun testValidateThrowsForNonEnumMember() {
        assertThrows(JsonException::class.java) {
            enumSchema.validate(jsonMapOf("result" to "pricing").toJsonValue())
        }
    }

    @Test
    public fun testValidateThrowsForNonStringEnumValue() {
        assertThrows(JsonException::class.java) {
            enumSchema.validate(jsonMapOf("result" to 4).toJsonValue())
        }
    }

    // MARK: parsing

    @Test
    public fun testParsesJsonSchemaStyleObject() {
        val json = """
            {
              "type": "object",
              "properties": {
                "result": {"type": "string", "enum": ["a", "b"]},
                "reason": {"type": "string", "description": "why"}
              },
              "required": ["result"]
            }
        """.trimIndent()

        val parsed = AirshipJsonSchema.fromJson(JsonValue.parseString(json))
        val type = parsed.type as ValueType.ObjectType

        assertEquals(ValueType.StringType(listOf("a", "b")), type.properties?.get("result")?.type)
        assertEquals(ValueType.StringType(), type.properties?.get("reason")?.type)
        assertEquals("why", type.properties?.get("reason")?.description)
        assertEquals(listOf("result"), type.required)
    }

    @Test
    public fun testParsesObjectWithoutProperties() {
        // `properties` is optional per JSON Schema — an object node may omit it.
        val parsed = AirshipJsonSchema.fromJson(
            JsonValue.parseString("""{"type": "object", "required": ["anything"]}""")
        )
        val type = parsed.type as ValueType.ObjectType
        assertNull(type.properties)

        // No declared properties → any object is accepted, but `required` still applies.
        parsed.validate(jsonMapOf("anything" to "present", "extra" to true).toJsonValue())
        assertThrows(JsonException::class.java) {
            parsed.validate(jsonMapOf("missing" to "required key").toJsonValue())
        }
    }

    @Test
    public fun testExplicitNullsAreTreatedAsAbsent() {
        // A serializer that writes nulls for absent fields must not break the parse.
        val parsed = AirshipJsonSchema.fromJson(
            JsonValue.parseString(
                """{"type":"object","description":null,"properties":null,"required":null}"""
            )
        )
        val type = parsed.type as ValueType.ObjectType

        assertNull(parsed.description)
        assertNull(type.properties)
        assertNull(type.required)
        assertEquals(AirshipJsonSchema.obj(), parsed)
    }

    @Test
    public fun testExplicitNullEnumIsTreatedAsAbsent() {
        val parsed = AirshipJsonSchema.fromJson(
            JsonValue.parseString("""{"type":"string","enum":null}""")
        )
        assertEquals(AirshipJsonSchema.string(), parsed)
    }

    @Test
    public fun testArrayWithoutItemsThrows() {
        // Unlike the optional keywords, a missing or null `items` is a malformed array node.
        assertThrows(JsonException::class.java) {
            AirshipJsonSchema.fromJson(JsonValue.parseString("""{"type":"array"}"""))
        }
        assertThrows(JsonException::class.java) {
            AirshipJsonSchema.fromJson(JsonValue.parseString("""{"type":"array","items":null}"""))
        }
    }

    @Test
    public fun testUnconstrainedObjectAcceptsAnything() {
        val schema = AirshipJsonSchema.obj()

        schema.validate(jsonMapOf("anything" to 1, "at" to "all").toJsonValue())
        assertThrows(JsonException::class.java) {
            schema.validate(JsonValue.wrap("not an object"))
        }
    }

    @Test
    public fun testUnknownTypeThrows() {
        assertThrows(JsonException::class.java) {
            AirshipJsonSchema.fromJson(JsonValue.parseString("""{"type": "tuple"}"""))
        }
    }

    @Test
    public fun testSchemaRoundTrips() {
        val original = AirshipJsonSchema.obj(
            properties = mapOf(
                "result" to AirshipJsonSchema.string(choices = listOf("a", "b"), description = "pick one"),
                "tags" to AirshipJsonSchema.array(items = AirshipJsonSchema.string()),
                "details" to AirshipJsonSchema.obj(
                    properties = mapOf("score" to AirshipJsonSchema.number()),
                    required = listOf("score")
                )
            ),
            required = listOf("result", "details")
        )

        assertEquals(original, AirshipJsonSchema.fromJson(original.toJsonValue()))
    }

    // MARK: vendor extensions (x-*)

    @Test
    public fun testParsesVendorExtensionKeysAtEveryLevel() {
        val json = """
            {
              "type": "object",
              "x-ua-report": true,
              "properties": {
                "result": {"type": "string", "x-ua-report-property": true},
                "reason": {"type": "string"}
              }
            }
        """.trimIndent()

        val parsed = AirshipJsonSchema.fromJson(JsonValue.parseString(json))
        assertEquals(JsonValue.wrap(true), parsed.extensions["x-ua-report"])

        val type = parsed.type as ValueType.ObjectType
        assertEquals(
            JsonValue.wrap(true),
            type.properties?.get("result")?.extensions?.get("x-ua-report-property")
        )
        assertTrue(type.properties?.get("reason")?.extensions?.isEmpty() == true)
    }

    @Test
    public fun testParseCapturesOnlyVendorPrefixedUnknownKeys() {
        val parsed = AirshipJsonSchema.fromJson(
            JsonValue.parseString("""{"type": "string", "x-keep": 1, "randomUnknown": "dropped"}""")
        )
        assertEquals(mapOf("x-keep" to JsonValue.wrap(1)), parsed.extensions)
    }

    @Test
    public fun testExtensionsRoundTrip() {
        val json = """
            {
              "type": "object",
              "x-ua-report": true,
              "properties": {
                "result": {"type": "string", "x-ua-report-property": true}
              }
            }
        """.trimIndent()

        val parsed = AirshipJsonSchema.fromJson(JsonValue.parseString(json))
        val reparsed = AirshipJsonSchema.fromJson(parsed.toJsonValue())

        // Equality compares extensions, so this also proves the serializer wrote them back
        // without clobbering a real keyword.
        assertEquals(parsed, reparsed)
        assertEquals(JsonValue.wrap(true), reparsed.extensions["x-ua-report"])
    }

    @Test
    public fun testNullValuedExtensionIsDroppedSoSchemasRoundTrip() {
        // A kept null would be omitted on the way back out, silently breaking equality.
        val parsed = AirshipJsonSchema.fromJson(
            JsonValue.parseString("""{"type":"string","x-null":null,"x-kept":1}""")
        )

        assertEquals(mapOf("x-kept" to JsonValue.wrap(1)), parsed.extensions)
        assertEquals(parsed, AirshipJsonSchema.fromJson(parsed.toJsonValue()))
    }

    @Test
    public fun testConstructorKeepsOnlyVendorExtensionKeys() {
        val schema = AirshipJsonSchema(
            type = ValueType.StringType(),
            extensions = mapOf(
                "x-ok" to JsonValue.wrap(true),
                "nope" to JsonValue.wrap("dropped")
            )
        )
        assertEquals(mapOf("x-ok" to JsonValue.wrap(true)), schema.extensions)
    }

    // MARK: non-object roots

    @Test
    public fun testParsesScalarEnumRoot() {
        val parsed = AirshipJsonSchema.fromJson(
            JsonValue.parseString("""{"type": "string", "enum": ["shipping", "quality"]}""")
        )
        assertEquals(AirshipJsonSchema.string(choices = listOf("shipping", "quality")), parsed)
    }

    @Test
    public fun testArrayRootRoundTripsAndValidates() {
        val original = AirshipJsonSchema.array(items = AirshipJsonSchema.string())
        val parsed = AirshipJsonSchema.fromJson(original.toJsonValue())
        assertEquals(original, parsed)

        parsed.validate(jsonListOf("a", "b").toJsonValue())
        assertThrows(JsonException::class.java) {
            parsed.validate(jsonMapOf("not" to "an array").toJsonValue())
        }
    }

    @Test
    public fun testScalarRootValidates() {
        val schema = AirshipJsonSchema.string(choices = listOf("a", "b"))

        schema.validate(JsonValue.wrap("a"))
        assertThrows(JsonException::class.java) {
            schema.validate(JsonValue.wrap("c"))
        }
    }
}
