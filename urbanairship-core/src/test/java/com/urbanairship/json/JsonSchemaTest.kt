/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonSchema.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class JsonSchemaTest {

    /** A schema exercising every value type, including a nested object and an array. */
    private val schema = JsonSchema.obj(
        properties = mapOf(
            "allow" to JsonSchema.boolean(),
            "reason" to JsonSchema.string(),
            "user" to JsonSchema.obj(
                properties = mapOf(
                    "age" to JsonSchema.integer(),
                    "tags" to JsonSchema.array(items = JsonSchema.string())
                ),
                required = listOf("age")
            )
        ),
        required = listOf("allow")
    )

    private val enumSchema = JsonSchema.obj(
        properties = mapOf("result" to JsonSchema.string(choices = listOf("shipping", "quality", "praise"))),
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
        val schema = JsonSchema.obj(
            properties = mapOf("known" to JsonSchema.string()),
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

        val parsed = JsonSchema.fromJson(JsonValue.parseString(json))
        val type = parsed.type as ValueType.ObjectType

        assertEquals(ValueType.StringType(listOf("a", "b")), type.properties?.get("result")?.type)
        assertEquals(ValueType.StringType(), type.properties?.get("reason")?.type)
        assertEquals("why", type.properties?.get("reason")?.description)
        assertEquals(listOf("result"), type.required)
    }

    @Test
    public fun testParsesObjectWithoutProperties() {
        // `properties` is optional per JSON Schema — an object node may omit it.
        val parsed = JsonSchema.fromJson(
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
    public fun testUnknownTypeThrows() {
        assertThrows(JsonException::class.java) {
            JsonSchema.fromJson(JsonValue.parseString("""{"type": "tuple"}"""))
        }
    }

    @Test
    public fun testSchemaRoundTrips() {
        val original = JsonSchema.obj(
            properties = mapOf(
                "result" to JsonSchema.string(choices = listOf("a", "b"), description = "pick one"),
                "tags" to JsonSchema.array(items = JsonSchema.string()),
                "details" to JsonSchema.obj(
                    properties = mapOf("score" to JsonSchema.number()),
                    required = listOf("score")
                )
            ),
            required = listOf("result", "details")
        )

        assertEquals(original, JsonSchema.fromJson(original.toJsonValue()))
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

        val parsed = JsonSchema.fromJson(JsonValue.parseString(json))
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
        val parsed = JsonSchema.fromJson(
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

        val parsed = JsonSchema.fromJson(JsonValue.parseString(json))
        val reparsed = JsonSchema.fromJson(parsed.toJsonValue())

        // Equality compares extensions, so this also proves the serializer wrote them back
        // without clobbering a real keyword.
        assertEquals(parsed, reparsed)
        assertEquals(JsonValue.wrap(true), reparsed.extensions["x-ua-report"])
    }

    @Test
    public fun testConstructorKeepsOnlyVendorExtensionKeys() {
        val schema = JsonSchema(
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
        val parsed = JsonSchema.fromJson(
            JsonValue.parseString("""{"type": "string", "enum": ["shipping", "quality"]}""")
        )
        assertEquals(JsonSchema.string(choices = listOf("shipping", "quality")), parsed)
    }

    @Test
    public fun testArrayRootRoundTripsAndValidates() {
        val original = JsonSchema.array(items = JsonSchema.string())
        val parsed = JsonSchema.fromJson(original.toJsonValue())
        assertEquals(original, parsed)

        parsed.validate(jsonListOf("a", "b").toJsonValue())
        assertThrows(JsonException::class.java) {
            parsed.validate(jsonMapOf("not" to "an array").toJsonValue())
        }
    }

    @Test
    public fun testScalarRootValidates() {
        val schema = JsonSchema.string(choices = listOf("a", "b"))

        schema.validate(JsonValue.wrap("a"))
        assertThrows(JsonException::class.java) {
            schema.validate(JsonValue.wrap("c"))
        }
    }
}
