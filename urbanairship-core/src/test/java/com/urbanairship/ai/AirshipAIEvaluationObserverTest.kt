/* Copyright Airship and Contributors */
package com.urbanairship.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.jsonMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The observer is the app's only window into what the model was asked and what it answered, so
 * what matters is that it fires for every outcome — a model that never ran is the common case
 * in the field and the one an app most needs to see.
 */
@RunWith(AndroidJUnit4::class)
public class AirshipAIEvaluationObserverTest {

    private val records = mutableListOf<AIEvaluationRecord>()
    private val observer = AIEvaluationObserver { records.add(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun evaluate(
        model: AIModel,
        context: AIContext = AIContext.EMPTY,
        evaluation: TestEvaluation = TestEvaluation()
    ): AIEvaluationResult<TestOutput> = AIEvaluator(
        CoroutineScope(UnconfinedTestDispatcher())
    ).evaluate(evaluation, model, context, observer)

    @Test
    public fun testCompletedReportsRawOutput(): Unit = runTest {
        evaluate(MockAIModel(response = { allowResponse(allow = true, reason = "ok") }))

        assertEquals(1, records.size)
        val outcome = records.first().outcome
        assertTrue(outcome is AIEvaluationRecord.Outcome.Completed)
        // Raw JSON rather than a typed value is what lets one observer serve every usage.
        assertEquals(
            "ok",
            (outcome as AIEvaluationRecord.Outcome.Completed).output.optMap().opt("reason").string
        )
        assertEquals("test_usage", records.first().usage.rawValue)
        assertEquals(1, records.first().attempts)
    }

    @Test
    public fun testUnavailableModelReports(): Unit = runTest {
        evaluate(
            MockAIModel(
                availability = AIModelAvailability.Unavailable(
                    AIModelAvailability.Reason.MissingModel
                )
            )
        )

        assertEquals(1, records.size)
        val outcome = records.first().outcome
        assertTrue(outcome is AIEvaluationRecord.Outcome.Skipped)
        assertEquals("Model unavailable", (outcome as AIEvaluationRecord.Outcome.Skipped).reason)
        // Never reached the model, so nothing was attempted.
        assertEquals(0, records.first().attempts)
    }

    @Test
    public fun testFailureReportsAttempts(): Unit = runTest {
        evaluate(MockAIModel(response = { throw SampleError() }, maxAttempts = 3))

        assertEquals(1, records.size)
        assertTrue(records.first().outcome is AIEvaluationRecord.Outcome.Failed)
        assertEquals(3, records.first().attempts)
    }

    @Test
    public fun testUnparseableOutputReportsOnceAsCompleted(): Unit = runTest {
        // The output passed schema validation, so it is reported as completed even though the
        // feature couldn't parse it — that's exactly when an app wants to see it.
        val evaluation = object : TestEvaluation() {
            override fun parseOutput(json: com.urbanairship.json.JsonValue): TestOutput =
                throw com.urbanairship.json.JsonException("nope")
        }

        val result = evaluate(MockAIModel(), evaluation = evaluation)

        assertTrue(result is AIEvaluationResult.Failed)
        assertEquals(1, records.size)
        assertTrue(records.first().outcome is AIEvaluationRecord.Outcome.Completed)
    }

    @Test
    public fun testRecordCarriesRequest(): Unit = runTest {
        val context = AIContext(listOf(AIContext.Item("User interests: cats")))

        evaluate(MockAIModel(), context)

        val request = records.first().request
        assertEquals("rules", request.instructions)
        // Context as offered — a model that trims to fit does so on its own copy.
        assertEquals(listOf("User interests: cats"), request.context.items.map { it.content })
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun testNoObserverIsFine(): Unit = runTest {
        val result = AIEvaluator(CoroutineScope(UnconfinedTestDispatcher()))
            .evaluate(TestEvaluation(), MockAIModel(), AIContext.EMPTY, observer = null)

        assertTrue(result is AIEvaluationResult.Completed)
    }

    @Test
    public fun testManagerForwardsObserver(): Unit = runTest {
        val manager = testManager()
        manager.registerModelFactory { MockAIModel() }
        manager.setEvaluationObserver(observer)

        manager.evaluate(TestEvaluation())

        assertEquals(1, records.size)
    }

    @Test
    public fun testClearingObserverStopsTheReports(): Unit = runTest {
        val manager = testManager()
        manager.registerModelFactory { MockAIModel() }

        manager.setEvaluationObserver(observer)
        manager.evaluate(TestEvaluation())
        assertEquals(1, records.size)

        manager.setEvaluationObserver(null)
        manager.evaluate(TestEvaluation())
        assertEquals(1, records.size)
    }

    @Test
    public fun testSchemaRetryIsReportedAsASingleRecord(): Unit = runTest {
        val model = MockAIModel(maxAttempts = 3)
        model.responses = mutableListOf(
            { jsonMapOf("unexpected_key" to "value").toJsonValue() },
            { allowResponse() }
        )

        evaluate(model)

        assertEquals(1, records.size)
        assertEquals(2, records.first().attempts)
    }
}
