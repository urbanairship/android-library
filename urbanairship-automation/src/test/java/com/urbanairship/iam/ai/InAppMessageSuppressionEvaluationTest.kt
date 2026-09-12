/* Copyright Airship and Contributors */
package com.urbanairship.iam.ai

import com.urbanairship.ai.EvaluationContext
import com.urbanairship.ai.Usage
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class InAppMessageSuppressionEvaluationTest {

    private val subject = InAppMessageSuppressionSubject(
        name = "Spring promo",
        extras = jsonMapOf("campaign" to "spring").toJsonValue(),
        priority = 3,
        hints = mapOf("surface" to "home")
    )

    private val evaluation = InAppMessageSuppressionEvaluation(
        condition = "the user rents trucks",
        subject = subject
    )

    @Test
    public fun testUsageAndSubject() {
        assertEquals(Usage.inAppMessageSuppression, evaluation.usage)
        assertEquals(subject, evaluation.subject)
    }

    @Test
    public fun testConditionGovernsFromTheInstructions() {
        assertTrue(evaluation.instructions().contains("<condition>the user rents trucks</condition>"))
        // Stated once: repeating it in the prompt would let the message content argue with it.
        assertFalse(evaluation.prompt(EvaluationContext.EMPTY).contains("rents trucks"))
    }

    @Test
    public fun testPromptCarriesTheMessageButNotTheHints() {
        val prompt = evaluation.prompt(EvaluationContext.EMPTY)

        assertTrue(prompt.contains("Message name: Spring promo"))
        assertTrue(prompt.contains("Message priority: 3"))
        assertTrue(prompt.contains("\"campaign\":\"spring\""))
        // Hints are the context provider's input, not the model's.
        assertFalse(prompt.contains("surface"))
        assertFalse(prompt.contains("home"))
    }

    @Test
    public fun testEmptyExtrasAreOmitted() {
        listOf(null, JsonValue.NULL, jsonMapOf().toJsonValue()).forEach { extras ->
            val prompt = InAppMessageSuppressionEvaluation(
                condition = "x",
                subject = InAppMessageSuppressionSubject(name = "Promo", extras = extras)
            ).prompt(EvaluationContext.EMPTY)

            assertFalse("extras=$extras", prompt.contains("Message Extras"))
        }
    }

    @Test
    public fun testContextIsRenderedAfterTheMessage() {
        val prompt = evaluation.prompt(
            EvaluationContext(
                listOf(
                    EvaluationContext.Item("Rented: 20ft (2024)"),
                    EvaluationContext.Item("Interests: cats")
                )
            )
        )

        assertTrue(prompt.endsWith("User context:\n- Rented: 20ft (2024)\n- Interests: cats"))
    }

    @Test
    public fun testOutputParses() {
        assertEquals(
            InAppMessageSuppressionEvaluation.Output(allow = false, reason = "not a renter"),
            evaluation.parseOutput(
                jsonMapOf("allow" to false, "reason" to "not a renter").toJsonValue()
            )
        )
    }

    @Test
    public fun testSchemaAcceptsTheOutputItAsksFor() {
        evaluation.schema.validate(
            jsonMapOf("allow" to true, "reason" to "renter").toJsonValue()
        )
    }
}
