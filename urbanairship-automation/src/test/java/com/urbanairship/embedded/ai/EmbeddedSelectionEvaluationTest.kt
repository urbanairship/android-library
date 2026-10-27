/* Copyright Airship and Contributors */
package com.urbanairship.embedded.ai

import com.urbanairship.ai.EvaluationContext
import com.urbanairship.embedded.AirshipEmbeddedInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddedSelectionEvaluationTest {

    private val evaluation = EmbeddedSelectionEvaluation(
        EmbeddedSelectionRequest(
            embeddedId = "home_banner",
            prompt = "Prefer cat offers",
            candidates = listOf(
                candidate("6dcbae8a-fb36-41e4-8d2b-875db9bdbb32", "dog clearance"),
                candidate("d01bc026-f5f9-45b5-abc7-ff8884539161", "cat sale")
            )
        )
    )

    private fun candidate(id: String, description: String) = AirshipEmbeddedInfo(
        instanceId = id,
        embeddedId = "home_banner",
        priority = 0,
        contentDescription = description
    )

    @Test
    fun testModelSeesShortIds() {
        val prompt = evaluation.prompt(EvaluationContext.EMPTY)

        assertTrue(prompt.contains("""{"id":"1","description":"dog clearance"}"""))
        assertTrue(prompt.contains("""{"id":"2","description":"cat sale"}"""))
        assertFalse(prompt.contains("6dcbae8a"))
        assertTrue(evaluation.schema.toString().contains(""""enum":["1","2"]"""))
    }

    @Test
    fun testOutputMapsBackToInstanceIds() {
        val json = JsonValue.parseString(
            """{"scores":[{"id":"2","score":9},{"id":"1","score":1}],"reason":"likes cats"}"""
        )
        evaluation.schema.validate(json)

        assertEquals(
            listOf(
                EmbeddedSelectionEvaluation.Output.CandidateScore("d01bc026-f5f9-45b5-abc7-ff8884539161", 9),
                EmbeddedSelectionEvaluation.Output.CandidateScore("6dcbae8a-fb36-41e4-8d2b-875db9bdbb32", 1)
            ),
            evaluation.parseOutput(json).scores
        )
    }

    @Test
    fun testInstanceIdIsNotAValidModelId() {
        val json = JsonValue.parseString(
            """{"scores":[{"id":"d01bc026-f5f9-45b5-abc7-ff8884539161","score":9}],"reason":"likes cats"}"""
        )

        assertThrows(JsonException::class.java) { evaluation.schema.validate(json) }
        assertThrows(JsonException::class.java) { evaluation.parseOutput(json) }
    }
}
