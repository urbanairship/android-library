/* Copyright Airship and Contributors */
package com.urbanairship.devapp.ai

import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeminiModelTest {

    /**
     * Gemini takes `required` as authored, so an optional property stays optional rather than
     * becoming nullable the way OpenAI strict mode forces. Airship's `x-*` extensions are
     * dropped either way.
     */
    @Test
    fun testOptionalPropertyStaysOptional() {
        val schema = AirshipJsonSchema.fromJson(
            JsonValue.parseString(
                """
                {
                  "type": "object",
                  "x-ua-report-property": true,
                  "properties": {
                    "topic": {
                      "type": "string",
                      "enum": ["shipping", "other"],
                      "x-ua-report-property": true
                    },
                    "note": { "type": "string", "description": "free text" }
                  },
                  "required": ["topic"]
                }
                """
            )
        )

        assertEquals(
            JsonValue.parseString(
                """
                {
                  "type": "object",
                  "required": ["topic"],
                  "properties": {
                    "topic": { "type": "string", "enum": ["shipping", "other"] },
                    "note": { "type": "string", "description": "free text" }
                  }
                }
                """
            ),
            responseSchemaOf(schema)
        )
    }

    /**
     * `properties: null` means "any object". Gemini can express that by omitting the key, so
     * unlike the strict-mode conversion this needs no special case.
     */
    @Test
    fun testOpenObjectOmitsProperties() {
        val schema = AirshipJsonSchema.fromJson(
            JsonValue.parseString("""{ "type": "object" }""")
        )

        assertEquals(
            JsonValue.parseString("""{ "type": "object" }"""),
            responseSchemaOf(schema)
        )
    }

    @Test
    fun testNestedObjectsAndArrays() {
        val schema = AirshipJsonSchema.fromJson(
            JsonValue.parseString(
                """
                {
                  "type": "object",
                  "description": "the answer",
                  "properties": {
                    "scores": {
                      "type": "array",
                      "items": { "type": "integer" }
                    },
                    "meta": {
                      "type": "object",
                      "properties": {
                        "confidence": { "type": "number" },
                        "certain": { "type": "boolean" }
                      },
                      "required": ["confidence"]
                    }
                  },
                  "required": ["scores", "meta"]
                }
                """
            )
        )

        assertEquals(
            JsonValue.parseString(
                """
                {
                  "type": "object",
                  "description": "the answer",
                  "required": ["scores", "meta"],
                  "properties": {
                    "scores": { "type": "array", "items": { "type": "integer" } },
                    "meta": {
                      "type": "object",
                      "required": ["confidence"],
                      "properties": {
                        "confidence": { "type": "number" },
                        "certain": { "type": "boolean" }
                      }
                    }
                  }
                }
                """
            ),
            responseSchemaOf(schema)
        )
    }

    private fun responseSchemaOf(schema: AirshipJsonSchema): JsonValue =
        GeminiModel.responseSchema(schema).toJsonValue()
}
