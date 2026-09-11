/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ai

import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ThomasAIInferenceOutcomeTest {

    @Test
    public fun testFailedPayloads() {
        assertEquals(
            JsonValue.parseString("""{ "status": "failed" }"""),
            ThomasAIInferenceOutcome.Failed.stateProjection
        )
        assertEquals(
            JsonValue.parseString("""{ "result": "failed" }"""),
            ThomasAIInferenceOutcome.Failed.reported
        )
    }

    @Test
    public fun testStateProjectionCarriesTheWholeOutput() {
        val output = JsonValue.parseString("""{ "topic": "shipping", "note": "private" }""")

        assertEquals(
            JsonValue.parseString(
                """{ "status": "complete", "result": { "topic": "shipping", "note": "private" } }"""
            ),
            ThomasAIInferenceOutcome.Complete(output, schema(FLAGGED_TOPIC)).stateProjection
        )
    }

    @Test
    public fun testOnlyFlaggedPropertiesAreReported() {
        val output = JsonValue.parseString("""{ "topic": "shipping", "note": "private" }""")

        assertEquals(
            JsonValue.parseString(
                """{ "result": "success", "output": { "topic": "shipping" } }"""
            ),
            ThomasAIInferenceOutcome.Complete(output, schema(FLAGGED_TOPIC)).reported
        )
    }

    @Test
    public fun testUnflaggedRootPrunesFlaggedChildren() {
        val schema = AirshipJsonSchema.fromJson(
            JsonValue.parseString(
                """
                {
                  "type": "object",
                  "properties": {
                    "topic": { "type": "string", "x-ua-report-property": true }
                  }
                }
                """
            )
        )
        val output = JsonValue.parseString("""{ "topic": "shipping" }""")

        assertEquals(
            JsonValue.parseString("""{ "result": "success" }"""),
            ThomasAIInferenceOutcome.Complete(output, schema).reported
        )
    }

    @Test
    public fun testUnflaggedContainerPrunesItsSubtree() {
        val schema = schema(
            """
            "detail": {
              "type": "object",
              "properties": {
                "topic": { "type": "string", "x-ua-report-property": true }
              }
            }
            """
        )
        val output = JsonValue.parseString("""{ "detail": { "topic": "shipping" } }""")

        assertEquals(
            JsonValue.parseString("""{ "result": "success" }"""),
            ThomasAIInferenceOutcome.Complete(output, schema).reported
        )
    }

    @Test
    public fun testFlaggedArrayReportsFlaggedItems() {
        val schema = schema(
            """
            "tags": {
              "type": "array",
              "x-ua-report-property": true,
              "items": { "type": "string", "x-ua-report-property": true }
            }
            """
        )
        val output = JsonValue.parseString("""{ "tags": ["a", "b"] }""")

        assertEquals(
            JsonValue.parseString("""{ "result": "success", "output": { "tags": ["a", "b"] } }"""),
            ThomasAIInferenceOutcome.Complete(output, schema).reported
        )
    }

    @Test
    public fun testAbsentPropertyIsOmitted() {
        val output = JsonValue.parseString("""{ "note": "private" }""")

        assertEquals(
            JsonValue.parseString("""{ "result": "success" }"""),
            ThomasAIInferenceOutcome.Complete(output, schema(FLAGGED_TOPIC)).reported
        )
    }

    /** A flagged object schema with [properties] inside it. */
    private fun schema(properties: String): AirshipJsonSchema = AirshipJsonSchema.fromJson(
        JsonValue.parseString(
            """
            {
              "type": "object",
              "x-ua-report-property": true,
              "properties": { $properties }
            }
            """
        )
    )

    private companion object {
        const val FLAGGED_TOPIC = """
            "topic": { "type": "string", "x-ua-report-property": true },
            "note": { "type": "string" }
        """
    }
}
