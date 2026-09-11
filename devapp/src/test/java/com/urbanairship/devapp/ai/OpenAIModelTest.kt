/* Copyright Airship and Contributors */
package com.urbanairship.devapp.ai

import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenAIModelTest {

    /**
     * Strict mode requires every property in `required` and `additionalProperties: false`, so
     * an optional property has to become nullable instead. Airship's `x-*` extensions are
     * dropped — strict mode rejects keywords it doesn't know.
     */
    @Test
    fun testOptionalPropertyBecomesNullable() {
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
                  "additionalProperties": false,
                  "required": ["topic", "note"],
                  "properties": {
                    "topic": { "type": "string", "enum": ["shipping", "other"] },
                    "note": { "type": ["string", "null"], "description": "free text" }
                  }
                }
                """
            ),
            strictSchemaOf(schema)
        )
    }

    @Test
    fun testNestedObjectsAndArrays() {
        val schema = AirshipJsonSchema.fromJson(
            JsonValue.parseString(
                """
                {
                  "type": "object",
                  "properties": {
                    "tags": { "type": "array", "items": { "type": "string" } },
                    "detail": {
                      "type": "object",
                      "properties": { "score": { "type": "integer" } },
                      "required": ["score"]
                    }
                  },
                  "required": ["tags", "detail"]
                }
                """
            )
        )

        assertEquals(
            JsonValue.parseString(
                """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["tags", "detail"],
                  "properties": {
                    "tags": { "type": "array", "items": { "type": "string" } },
                    "detail": {
                      "type": "object",
                      "additionalProperties": false,
                      "required": ["score"],
                      "properties": { "score": { "type": "integer" } }
                    }
                  }
                }
                """
            ),
            strictSchemaOf(schema)
        )
    }

    private fun strictSchemaOf(schema: AirshipJsonSchema): JsonValue =
        OpenAIModel.strictSchema(schema).toJsonValue()
}
