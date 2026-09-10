/* Copyright Airship and Contributors */
package com.urbanairship.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.urbanairship.PrivacyManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AirshipAITest {

    // MARK: value types

    @Test
    public fun testResultOutputOnlyForCompleted() {
        assertEquals(7, AIEvaluationResult.Completed(7).output)
        assertNull(AIEvaluationResult.Skipped("nope").output)
        assertNull(AIEvaluationResult.Failed(SampleError()).output)
    }

    @Test
    public fun testEmptyContextIsEmpty() {
        assertTrue(AIContext.EMPTY.items.isEmpty())
        assertNull(AIContext.EMPTY.renderBullets())
    }

    @Test
    public fun testContextRenderBulletsJoinsItemsInOrderWithoutPriorities() {
        val context = AIContext(
            listOf(
                AIContext.Item("b", priority = 2.0),
                AIContext.Item("a", priority = 0.0),
                AIContext.Item("", priority = 0.0)
            )
        )

        // Order preserved, empty content skipped, priority never rendered.
        assertEquals("- b\n- a", context.renderBullets())
    }

    @Test
    public fun testDroppingLowestPriorityItemDropsEarliestLeastImportant() {
        // Lower value is more important, so the highest value is dropped first.
        var context = AIContext(
            listOf(
                AIContext.Item("keep-important", priority = 0.0),
                AIContext.Item("drop-first", priority = 2.0),
                AIContext.Item("drop-second", priority = 2.0),
                AIContext.Item("keep-mid", priority = 1.0)
            )
        )

        var dropped = context.droppingLowestPriorityItem()
        assertEquals("drop-first", dropped?.second?.content)
        context = dropped!!.first
        assertEquals(listOf("keep-important", "drop-second", "keep-mid"), context.items.map { it.content })

        dropped = context.droppingLowestPriorityItem()
        assertEquals("drop-second", dropped?.second?.content)
        context = dropped!!.first
        assertEquals(listOf("keep-important", "keep-mid"), context.items.map { it.content })

        dropped = context.droppingLowestPriorityItem()
        assertEquals("keep-mid", dropped?.second?.content)
        context = dropped!!.first
        assertEquals(listOf("keep-important"), context.items.map { it.content })

        assertNull(AIContext.EMPTY.droppingLowestPriorityItem())
    }

    @Test
    public fun testAppendingKeepsOrderWithOtherItemsLast() {
        // Same value → the appended (later) item is dropped last, so it wins the tie.
        val base = AIContext(listOf(AIContext.Item("provider", priority = 1.0)))
        val merged = base.appending(
            AIContext(listOf(AIContext.Item("authored", priority = 1.0)))
        )

        assertEquals(listOf("provider", "authored"), merged.items.map { it.content })
        assertEquals("provider", merged.droppingLowestPriorityItem()?.second?.content)
        assertEquals(base, base.appending(AIContext.EMPTY))
    }

    @Test
    public fun testUsagesWithSameRawValueAreEqualWhateverTheSubject() {
        // A ModelResolver is handed a `Usage<*>` and compares it against a feature's own key.
        val erased: AIUsage<*> = AIUsage<String>("test_usage")
        assertEquals(testUsage, erased)
        assertEquals(testUsage.hashCode(), erased.hashCode())
    }

    @Test
    public fun testDroppingContextItemFromRequestLeavesTheOriginalIntact() {
        val context = AIContext(
            listOf(
                AIContext.Item("keep", priority = 0.0),
                AIContext.Item("drop", priority = 1.0)
            )
        )
        val request = AIModelRequest(
            instructions = "rules",
            schema = testSchema,
            context = context,
            render = { rendered -> rendered.renderBullets() ?: "" }
        )

        val (trimmed, dropped) = requireNotNull(request.droppingLowestPriorityContextItem())

        assertEquals("drop", dropped.content)
        assertEquals("- keep", trimmed.prompt())
        // The request handed to the model is unchanged, so the reported record keeps the
        // context as it was offered.
        assertEquals("- keep\n- drop", request.prompt())
    }

    // MARK: evaluator

    private suspend fun eval(
        model: AIModel,
        context: AIContext = AIContext.EMPTY,
        evaluation: TestEvaluation = TestEvaluation(),
        maxResponseTimeout: Duration = AIEvaluator.DEFAULT_MAX_RESPONSE_TIMEOUT
    ): AIEvaluationResult<TestOutput> =
        testEvaluator(maxResponseTimeout).evaluate(evaluation, model, context)

    @Test
    public fun testCompletedWhenModelSucceeds(): Unit = runTest {
        val model = MockAIModel(response = { allowResponse(allow = false, reason = "not relevant") })
        val context = AIContext(listOf(AIContext.Item("likes hiking")))

        val result = eval(model, context)

        assertEquals(TestOutput(allow = false, reason = "not relevant"), result.output)
        assertEquals("rules", model.lastRequest?.instructions)
        assertEquals("subject", model.lastRequest?.prompt())
        assertEquals(context, model.lastRequest?.context)
        assertEquals(testSchema, model.lastRequest?.schema)
    }

    @Test
    public fun testSkippedWhenModelUnavailable(): Unit = runTest {
        val model = MockAIModel(
            availability = AIModelAvailability.Unavailable(
                AIModelAvailability.Reason.DeviceNotEligible
            )
        )

        assertTrue(eval(model) is AIEvaluationResult.Skipped)
        assertEquals(0, model.respondCallCount)
    }

    @Test
    public fun testFailedWhenModelThrows(): Unit = runTest {
        val result = eval(MockAIModel(response = { throw SampleError() }))
        assertTrue(result is AIEvaluationResult.Failed)
    }

    @Test
    public fun testFailedWhenResponseDoesNotMatchSchema(): Unit = runTest {
        val model = MockAIModel(response = { offSchemaResponse() })
        assertTrue(eval(model) is AIEvaluationResult.Failed)
    }

    @Test
    public fun testFailedWhenOutputCannotBeParsed(): Unit = runTest {
        // Passes schema validation, but the evaluation's own parser rejects it.
        val evaluation = object : TestEvaluation() {
            override fun parseOutput(json: com.urbanairship.json.JsonValue): TestOutput =
                throw com.urbanairship.json.JsonException("nope")
        }

        val result = eval(MockAIModel(), evaluation = evaluation)
        assertTrue(result is AIEvaluationResult.Failed)
    }

    @Test
    public fun testUsesEmptyContextWhenNoProvider(): Unit = runTest {
        val model = MockAIModel()
        eval(model)
        assertEquals(AIContext.EMPTY, model.lastRequest?.context)
    }

    // MARK: retry / timeout

    @Test
    public fun testRetriesUntilOutputConformsToSchema(): Unit = runTest {
        val model = MockAIModel(maxAttempts = 3)
        model.responses = mutableListOf(
            { offSchemaResponse() },
            { allowResponse(allow = true, reason = "second try") }
        )

        val result = eval(model)

        assertEquals(TestOutput(allow = true, reason = "second try"), result.output)
        assertEquals(2, model.respondCallCount)
    }

    @Test
    public fun testFailsAfterExhaustingAttempts(): Unit = runTest {
        val model = MockAIModel(response = { offSchemaResponse() }, maxAttempts = 2)

        assertTrue(eval(model) is AIEvaluationResult.Failed)
        assertEquals(2, model.respondCallCount)
    }

    @Test
    public fun testCancellationErrorIsNotRetried(): Unit = runTest {
        val model = MockAIModel(response = { throw CancellationException() }, maxAttempts = 5)

        assertTrue(eval(model) is AIEvaluationResult.Failed)
        // Cancellation is terminal — without the dedicated branch, all 5 attempts would run.
        assertEquals(1, model.respondCallCount)
    }

    @Test
    public fun testRetriesUntilTheModelSaysToStop(): Unit = runTest {
        val model = MockAIModel(response = { throw SampleError() }, maxAttempts = 3)

        assertTrue(eval(model) is AIEvaluationResult.Failed)
        assertEquals(3, model.respondCallCount)
    }

    @Test
    public fun testCeilingTerminatesSlowModel(): Unit = runTest {
        val model = MockAIModel()
        model.respondDelay = 60.seconds

        assertTrue(eval(model, maxResponseTimeout = 100.milliseconds) is AIEvaluationResult.Failed)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun testRetryDelayIsAwaitedBetweenAttempts(): Unit = runTest {
        val model = MockAIModel(response = { throw SampleError() }, maxAttempts = 3)
        model.retryDelay = 2.seconds

        val started = currentTime
        eval(model)

        // Two failures, so two delays before the third attempt gives up.
        assertEquals(4.seconds.inWholeMilliseconds, currentTime - started)
        assertEquals(3, model.respondCallCount)
    }

    @Test
    public fun testRetryDelayIsClampedToTheCeiling(): Unit = runTest {
        // An app-supplied delay past the ceiling must not park the evaluation forever.
        val model = MockAIModel(response = { throw SampleError() }, maxAttempts = 5)
        model.retryDelay = Duration.INFINITE

        assertTrue(eval(model, maxResponseTimeout = 1.seconds) is AIEvaluationResult.Failed)
    }

    @Test
    public fun testSchemaMismatchReachesRetryDecisionWrapped(): Unit = runTest {
        // A model needs to tell "answered but didn't conform" apart from a thrown failure.
        val model = MockAIModel(response = { offSchemaResponse() }, maxAttempts = 2)

        eval(model)

        assertTrue(model.retryErrors.all { it is AISchemaValidationException })
    }

    @Test
    public fun testThrownErrorReachesRetryDecisionUnwrapped(): Unit = runTest {
        val model = MockAIModel(response = { throw SampleError() }, maxAttempts = 2)

        eval(model)

        assertTrue(model.retryErrors.all { it is SampleError })
    }

    @Test
    public fun testDefaultBackoffRetriesSchemaMismatchImmediately() {
        val error = AISchemaValidationException(SampleError())
        assertEquals(AIRetryDecision.Retry(Duration.ZERO), AIRetryDecision.defaultBackoff(error, 1))
        assertEquals(AIRetryDecision.Retry(Duration.ZERO), AIRetryDecision.defaultBackoff(error, 2))
        assertEquals(AIRetryDecision.Fail, AIRetryDecision.defaultBackoff(error, 3))
    }

    @Test
    public fun testDefaultBackoffBacksOffOnAnyOtherError() {
        val error = SampleError()
        assertEquals(AIRetryDecision.Retry(1.seconds), AIRetryDecision.defaultBackoff(error, 1))
        assertEquals(AIRetryDecision.Retry(4.seconds), AIRetryDecision.defaultBackoff(error, 2))
        assertEquals(AIRetryDecision.Fail, AIRetryDecision.defaultBackoff(error, 3))
    }

    @Test
    public fun testModelWithNoRetryDecisionUsesTheFrameworkDefault(): Unit = runTest {
        val model = MockAIModel(response = { throw SampleError() })
        model.retryDecision = null

        assertTrue(eval(model) is AIEvaluationResult.Failed)
        // Three attempts, per defaultBackoff.
        assertEquals(AIRetryDecision.DEFAULT_MAX_ATTEMPTS, model.respondCallCount)
    }

    @Test
    public fun testErrorsArePropagatedRatherThanRetried(): Unit = runTest {
        // An Error is not a failure to fail open on — retrying an OOM makes it worse.
        val model = MockAIModel(response = { throw OutOfMemoryError("nope") }, maxAttempts = 5)

        assertThrows(OutOfMemoryError::class.java) { runBlocking { eval(model) } }
        assertEquals(1, model.respondCallCount)
    }

    @Test
    public fun testResultsAndOutcomesCompareByValue() {
        assertEquals(AIEvaluationResult.Skipped("nope"), AIEvaluationResult.Skipped("nope"))
        assertEquals(AIEvaluationResult.Completed(7), AIEvaluationResult.Completed(7))
        assertEquals(
            AIEvaluationRecord.Outcome.Skipped("nope"),
            AIEvaluationRecord.Outcome.Skipped("nope")
        )
    }

    // MARK: manager

    @Test
    public fun testManagerUsesConfiguredModel(): Unit = runTest {
        val model = MockAIModel(response = { allowResponse(allow = false, reason = "override") })
        val manager = testManager()
        manager.setModelResolver { AIModelSelector.Custom(model) }

        val result = manager.evaluate(TestEvaluation())

        assertEquals(false, result.output?.allow)
        assertEquals(1, model.respondCallCount)
    }

    @Test
    public fun testManagerAppendsAdditionalContextAfterProviderContext(): Unit = runTest {
        val model = MockAIModel()
        val manager = testManager()
        manager.setModelResolver { AIModelSelector.Custom(model) }
        manager.setContextProvider(
            testUsage,
            itemsProvider(AIContext.Item("provider", priority = 0.0))
        )

        manager.evaluate(
            TestEvaluation(),
            additionalContext = AIContext(
                listOf(AIContext.Item("authored", priority = 5.0))
            )
        )

        assertEquals(
            listOf("provider", "authored"),
            model.lastRequest?.context?.items?.map { it.content }
        )
    }

    @Test
    public fun testDefaultContextProviderIsOnlyAFallback(): Unit = runTest {
        val model = MockAIModel()
        val manager = testManager()
        manager.setModelResolver { AIModelSelector.Custom(model) }
        manager.setDefaultContextProvider {
            AIContext(listOf(AIContext.Item("default")))
        }

        manager.evaluate(TestEvaluation())
        assertEquals(listOf("default"), model.lastRequest?.context?.items?.map { it.content })

        // A usage-specific provider wins outright; the two are never combined.
        manager.setContextProvider(testUsage, itemsProvider(AIContext.Item("specific")))
        manager.evaluate(TestEvaluation())
        assertEquals(listOf("specific"), model.lastRequest?.context?.items?.map { it.content })
    }

    @Test
    public fun testClearingContextProviderFallsBackToDefaultProvider(): Unit = runTest {
        val manager = testManager()
        manager.setDefaultContextProvider {
            AIContext(listOf(AIContext.Item("default")))
        }
        manager.setContextProvider(testUsage, itemsProvider(AIContext.Item("specific")))
        manager.setContextProvider(testUsage, null)

        assertEquals(
            listOf("default"),
            manager.fetchContext(testUsage, Unit).items.map { it.content }
        )
    }

    // MARK: requiresContext

    @Test
    public fun testSkipsContextRequiringEvaluationWhenContextEmpty(): Unit = runTest {
        val model = MockAIModel()
        val manager = testManager()
        manager.setModelResolver { AIModelSelector.Custom(model) }
        // No provider registered, so the resolved context is empty.

        assertTrue(manager.evaluate(ContextRequiredEvaluation()) is AIEvaluationResult.Skipped)
        assertEquals(0, model.respondCallCount)
    }

    @Test
    public fun testRunsContextRequiringEvaluationWhenProviderSuppliesContext(): Unit = runTest {
        val model = MockAIModel()
        val manager = testManager()
        manager.setModelResolver { AIModelSelector.Custom(model) }
        manager.setContextProvider(testUsage, itemsProvider(AIContext.Item("likes hiking")))

        assertNotNull(manager.evaluate(ContextRequiredEvaluation()).output)
        assertEquals(1, model.respondCallCount)
    }

    @Test
    public fun testRunsContextRequiringEvaluationWhenOnlyAdditionalContextProvided(): Unit = runTest {
        // The gate is on the merged context, so caller-supplied context satisfies it even
        // with no provider registered.
        val model = MockAIModel()
        val manager = testManager()
        manager.setModelResolver { AIModelSelector.Custom(model) }

        val result = manager.evaluate(
            ContextRequiredEvaluation(),
            additionalContext = AIContext(listOf(AIContext.Item("authored")))
        )

        assertNotNull(result.output)
        assertEquals(1, model.respondCallCount)
    }

    // MARK: context providers

    @Test
    public fun testProviderFailureDegradesToEmptyContext(): Unit = runTest {
        // The provider is app code on a display path; a throw must not take the display down.
        val manager = testManager()
        manager.setContextProvider(testUsage) { throw SampleError() }

        assertEquals(AIContext.EMPTY, manager.fetchContext(testUsage, Unit))
    }

    @Test
    public fun testProviderSubjectTypeMismatchDegradesToEmptyContext(): Unit = runTest {
        // Two features sharing a usage key with different subject types.
        val manager = testManager()
        val stringUsage = AIUsage<String>(testUsage.rawValue)
        manager.setContextProvider(stringUsage) { subject ->
            AIContext(listOf(AIContext.Item(subject)))
        }

        assertEquals(AIContext.EMPTY, manager.fetchContext(testUsage, Unit))
    }

    // MARK: per-usage model resolution

    @Test
    public fun testDefaultModelFactoryIsInvokedOnce() {
        // A factory that really constructs a backend must not run per evaluation.
        var invocations = 0
        val manager = testManager()
        manager.registerModelFactory {
            invocations += 1
            MockAIModel()
        }

        val first = manager.model(testUsage)
        val second = manager.model(testUsage)

        assertEquals(1, invocations)
        assertSame(first, second)
        assertSame(first, manager.defaultModel)
    }

    @Test
    public fun testModelIsNullWhenNoneConfigured() {
        assertNull(testManager().model(AIUsage<Unit>("test")))
    }

    @Test
    public fun testModelReturnsDefaultFactoryModelWhenNoResolver() {
        val model = MockAIModel()
        val manager = testManager()
        manager.registerModelFactory { model }

        assertSame(model, manager.model(AIUsage<Unit>("any_usage")))
        assertSame(model, manager.defaultModel)
    }

    @Test
    public fun testResolverWinsOverDefaultFactory() {
        val defaultModel = MockAIModel()
        val usageModel = MockAIModel()
        val manager = testManager()
        manager.registerModelFactory { defaultModel }
        manager.setModelResolver { usage ->
            if (usage == testUsage) {
                AIModelSelector.Custom(usageModel)
            } else {
                AIModelSelector.DefaultModel
            }
        }

        assertSame(usageModel, manager.model(testUsage))
        assertSame(defaultModel, manager.model(AIUsage<Unit>("other")))
    }

    @Test
    public fun testClearingResolverFallsBackToDefaultFactory() {
        val defaultModel = MockAIModel()
        val manager = testManager()
        manager.registerModelFactory { defaultModel }
        manager.setModelResolver { AIModelSelector.Custom(MockAIModel()) }
        manager.setModelResolver(null)

        assertSame(defaultModel, manager.model(testUsage))
    }

    // MARK: privacy manager gating

    @Test
    public fun testModelIsNullWhenAIDisabled() {
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.NONE))
        manager.registerModelFactory { MockAIModel() }

        assertNull(manager.model(testUsage))
        assertNull(manager.defaultModel)
    }

    @Test
    public fun testModelIsResolvedWhenAIEnabled() {
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.ON_DEVICE_AI))
        manager.registerModelFactory { MockAIModel() }

        assertEquals(AIModelAvailability.Available, manager.model(testUsage)?.availability)
        assertEquals(AIModelAvailability.Available, manager.defaultModel?.availability)
    }

    @Test
    public fun testGatedModelReportsNotEnabledWhenAIDisabled() {
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.NONE))
        manager.registerModelFactory { MockAIModel() }

        // Unlike model(), gatedModel() still returns an instance — just one reporting
        // NotEnabled — so a caller holding onto it sees the gate reflected in its
        // availability rather than losing the reference outright.
        assertEquals(
            AIModelAvailability.Unavailable(AIModelAvailability.Reason.NotEnabled),
            manager.gatedModel(testUsage)?.availability
        )
    }

    @Test
    public fun testGatedModelStaysInSyncAsPrivacyManagerToggles() {
        val privacyManager = testPrivacyManager()
        val manager = testManager(privacyManager)
        manager.registerModelFactory { MockAIModel() }

        // A single resolved reference, held across the toggle — mirrors a caller that
        // resolves once and caches the result.
        val resolved = manager.gatedModel(testUsage)
        assertEquals(AIModelAvailability.Available, resolved?.availability)

        privacyManager.disable(PrivacyManager.Feature.ON_DEVICE_AI)
        assertEquals(
            AIModelAvailability.Unavailable(AIModelAvailability.Reason.NotEnabled),
            resolved?.availability
        )

        privacyManager.enable(PrivacyManager.Feature.ON_DEVICE_AI)
        assertEquals(AIModelAvailability.Available, resolved?.availability)
    }

    @Test
    public fun testGatedModelAvailabilityUpdatesEmitsOnPrivacyManagerChange(): Unit = runTest {
        val privacyManager = testPrivacyManager()
        val manager = testManager(privacyManager)
        manager.registerModelFactory { MockAIModel() }

        val resolved = requireNotNull(manager.gatedModel(testUsage))

        resolved.availabilityUpdates.test {
            assertEquals(AIModelAvailability.Available, awaitItem())

            privacyManager.disable(PrivacyManager.Feature.ON_DEVICE_AI)
            assertEquals(
                AIModelAvailability.Unavailable(AIModelAvailability.Reason.NotEnabled),
                awaitItem()
            )

            privacyManager.enable(PrivacyManager.Feature.ON_DEVICE_AI)
            assertEquals(AIModelAvailability.Available, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun testEvaluateSkipsWhenAIDisabled(): Unit = runTest {
        val model = MockAIModel()
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.NONE))
        manager.setModelResolver { AIModelSelector.Custom(model) }

        assertTrue(manager.evaluate(TestEvaluation()) is AIEvaluationResult.Skipped)
        assertEquals(0, model.respondCallCount)
    }

    @Test
    public fun testFetchContextReturnsEmptyWhenAIDisabled(): Unit = runTest {
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.NONE))
        manager.setContextProvider(testUsage, itemsProvider(AIContext.Item("likes hiking")))

        assertEquals(AIContext.EMPTY, manager.fetchContext(testUsage, Unit))
    }
}
