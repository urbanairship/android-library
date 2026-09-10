/* Copyright Airship and Contributors */
package com.urbanairship.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.urbanairship.PrivacyManager
import com.urbanairship.json.JsonValue
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
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
public class AirshipAiTest {

    // MARK: value types

    @Test
    public fun testResultOutputOnlyForCompleted() {
        assertEquals(7, EvaluationResult.Completed(7).output)
        assertNull(EvaluationResult.Skipped("nope").output)
        assertNull(EvaluationResult.Failed(SampleError()).output)
    }

    @Test
    public fun testEmptyContextIsEmpty() {
        assertTrue(EvaluationContext.EMPTY.items.isEmpty())
        assertNull(EvaluationContext.EMPTY.renderBullets())
    }

    @Test
    public fun testContextRenderBulletsJoinsItemsInOrderWithoutPriorities() {
        val context = EvaluationContext(
            listOf(
                EvaluationContext.Item("b", priority = 2.0),
                EvaluationContext.Item("a", priority = 0.0),
                EvaluationContext.Item("", priority = 0.0)
            )
        )

        // Order preserved, empty content skipped, priority never rendered.
        assertEquals("- b\n- a", context.renderBullets())
    }

    @Test
    public fun testDroppingLowestPriorityItemDropsEarliestLeastImportant() {
        // Lower value is more important, so the highest value is dropped first.
        var context = EvaluationContext(
            listOf(
                EvaluationContext.Item("keep-important", priority = 0.0),
                EvaluationContext.Item("drop-first", priority = 2.0),
                EvaluationContext.Item("drop-second", priority = 2.0),
                EvaluationContext.Item("keep-mid", priority = 1.0)
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

        assertNull(EvaluationContext.EMPTY.droppingLowestPriorityItem())
    }

    @Test
    public fun testAppendingKeepsOrderWithOtherItemsLast() {
        // Same value → the appended (later) item is dropped last, so it wins the tie.
        val base = EvaluationContext(listOf(EvaluationContext.Item("provider", priority = 1.0)))
        val merged = base.appending(
            EvaluationContext(listOf(EvaluationContext.Item("authored", priority = 1.0)))
        )

        assertEquals(listOf("provider", "authored"), merged.items.map { it.content })
        assertEquals("provider", merged.droppingLowestPriorityItem()?.second?.content)
        assertEquals(base, base.appending(EvaluationContext.EMPTY))
    }

    @Test
    public fun testUsagesWithSameRawValueAreEqualWhateverTheSubject() {
        // A ModelResolver is handed a `Usage<*>` and compares it against a feature's own key.
        val erased: Usage<*> = Usage<String>("test_usage")
        assertEquals(testUsage, erased)
        assertEquals(testUsage.hashCode(), erased.hashCode())
    }

    @Test
    public fun testDroppingContextItemFromRequestLeavesTheOriginalIntact() {
        val context = EvaluationContext(
            listOf(
                EvaluationContext.Item("keep", priority = 0.0),
                EvaluationContext.Item("drop", priority = 1.0)
            )
        )
        val request = ModelRequest(
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
        model: ModelAdapter,
        context: EvaluationContext = EvaluationContext.EMPTY,
        evaluation: TestEvaluation = TestEvaluation(),
        maxResponseTimeout: Duration = Evaluator.DEFAULT_MAX_RESPONSE_TIMEOUT
    ): EvaluationResult<TestOutput> =
        testEvaluator(maxResponseTimeout).evaluate(evaluation, model, context)

    @Test
    public fun testCompletedWhenModelSucceeds(): Unit = runTest {
        val model = MockModel(response = { allowResponse(allow = false, reason = "not relevant") })
        val context = EvaluationContext(listOf(EvaluationContext.Item("likes hiking")))

        val result = eval(model, context)

        assertEquals(TestOutput(allow = false, reason = "not relevant"), result.output)
        assertEquals("rules", model.lastRequest?.instructions)
        assertEquals("subject", model.lastRequest?.prompt())
        assertEquals(context, model.lastRequest?.context)
        assertEquals(testSchema, model.lastRequest?.schema)
    }

    @Test
    public fun testSkippedWhenModelUnavailable(): Unit = runTest {
        val model = MockModel(
            availability = ModelAvailability.Unavailable(
                ModelAvailability.Reason.DeviceNotEligible
            )
        )

        assertTrue(eval(model) is EvaluationResult.Skipped)
        assertEquals(0, model.respondCallCount)
    }

    @Test
    public fun testFailedWhenModelThrows(): Unit = runTest {
        val result = eval(MockModel(response = { throw SampleError() }))
        assertTrue(result is EvaluationResult.Failed)
    }

    @Test
    public fun testFailedWhenResponseDoesNotMatchSchema(): Unit = runTest {
        val model = MockModel(response = { offSchemaResponse() })
        assertTrue(eval(model) is EvaluationResult.Failed)
    }

    @Test
    public fun testFailedWhenOutputCannotBeParsed(): Unit = runTest {
        // Passes schema validation, but the evaluation's own parser rejects it.
        val evaluation = object : TestEvaluation() {
            override fun parseOutput(json: com.urbanairship.json.JsonValue): TestOutput =
                throw com.urbanairship.json.JsonException("nope")
        }

        val result = eval(MockModel(), evaluation = evaluation)
        assertTrue(result is EvaluationResult.Failed)
    }

    @Test
    public fun testUsesEmptyContextWhenNoProvider(): Unit = runTest {
        val model = MockModel()
        eval(model)
        assertEquals(EvaluationContext.EMPTY, model.lastRequest?.context)
    }

    // MARK: retry / timeout

    @Test
    public fun testRetriesUntilOutputConformsToSchema(): Unit = runTest {
        val model = MockModel(maxAttempts = 3)
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
        val model = MockModel(response = { offSchemaResponse() }, maxAttempts = 2)

        assertTrue(eval(model) is EvaluationResult.Failed)
        assertEquals(2, model.respondCallCount)
    }

    @Test
    public fun testCancellationErrorIsNotRetried(): Unit = runTest {
        val model = MockModel(response = { throw CancellationException() }, maxAttempts = 5)

        assertTrue(eval(model) is EvaluationResult.Failed)
        // Cancellation is terminal — without the dedicated branch, all 5 attempts would run.
        assertEquals(1, model.respondCallCount)
    }

    @Test
    public fun testRetriesUntilTheModelSaysToStop(): Unit = runTest {
        val model = MockModel(response = { throw SampleError() }, maxAttempts = 3)

        assertTrue(eval(model) is EvaluationResult.Failed)
        assertEquals(3, model.respondCallCount)
    }

    @Test
    public fun testCeilingTerminatesSlowModel(): Unit = runTest {
        val model = MockModel()
        model.respondDelay = 60.seconds

        assertTrue(eval(model, maxResponseTimeout = 100.milliseconds) is EvaluationResult.Failed)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun testRetryDelayIsAwaitedBetweenAttempts(): Unit = runTest {
        val model = MockModel(response = { throw SampleError() }, maxAttempts = 3)
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
        val model = MockModel(response = { throw SampleError() }, maxAttempts = 5)
        model.retryDelay = Duration.INFINITE

        assertTrue(eval(model, maxResponseTimeout = 1.seconds) is EvaluationResult.Failed)
    }

    @Test
    public fun testSchemaMismatchReachesRetryDecisionWrapped(): Unit = runTest {
        // A model needs to tell "answered but didn't conform" apart from a thrown failure.
        val model = MockModel(response = { offSchemaResponse() }, maxAttempts = 2)

        eval(model)

        assertTrue(model.retryErrors.all { it is SchemaValidationException })
    }

    @Test
    public fun testThrownErrorReachesRetryDecisionUnwrapped(): Unit = runTest {
        val model = MockModel(response = { throw SampleError() }, maxAttempts = 2)

        eval(model)

        assertTrue(model.retryErrors.all { it is SampleError })
    }

    @Test
    public fun testDefaultBackoffRetriesSchemaMismatchImmediately() {
        val error = SchemaValidationException(SampleError())
        assertEquals(RetryDecision.Retry(Duration.ZERO), RetryDecision.defaultBackoff(error, 1))
        assertEquals(RetryDecision.Retry(Duration.ZERO), RetryDecision.defaultBackoff(error, 2))
        assertEquals(RetryDecision.Fail, RetryDecision.defaultBackoff(error, 3))
    }

    @Test
    public fun testDefaultBackoffBacksOffOnAnyOtherError() {
        val error = SampleError()
        assertEquals(RetryDecision.Retry(1.seconds), RetryDecision.defaultBackoff(error, 1))
        assertEquals(RetryDecision.Retry(4.seconds), RetryDecision.defaultBackoff(error, 2))
        assertEquals(RetryDecision.Fail, RetryDecision.defaultBackoff(error, 3))
    }

    @Test
    public fun testModelWithNoRetryDecisionUsesTheFrameworkDefault(): Unit = runTest {
        val model = MockModel(response = { throw SampleError() })
        model.retryDecision = null

        assertTrue(eval(model) is EvaluationResult.Failed)
        // Three attempts, per defaultBackoff.
        assertEquals(RetryDecision.DEFAULT_MAX_ATTEMPTS, model.respondCallCount)
    }

    @Test
    public fun testErrorsArePropagatedRatherThanRetried(): Unit = runTest {
        // An Error is not a failure to fail open on — retrying an OOM makes it worse.
        val model = MockModel(response = { throw OutOfMemoryError("nope") }, maxAttempts = 5)

        assertThrows(OutOfMemoryError::class.java) { runBlocking { eval(model) } }
        assertEquals(1, model.respondCallCount)
    }

    @Test
    public fun testResultsAndOutcomesCompareByValue() {
        assertEquals(EvaluationResult.Skipped("nope"), EvaluationResult.Skipped("nope"))
        assertEquals(EvaluationResult.Completed(7), EvaluationResult.Completed(7))
        assertEquals(
            EvaluationRecord.Outcome.Skipped("nope"),
            EvaluationRecord.Outcome.Skipped("nope")
        )
    }

    // MARK: manager

    @Test
    public fun testManagerUsesConfiguredModel(): Unit = runTest {
        val model = MockModel(response = { allowResponse(allow = false, reason = "override") })
        val manager = testManager()
        manager.setModelResolver { ModelSelector.Custom(model) }

        val result = manager.evaluate(TestEvaluation())

        assertEquals(false, result.output?.allow)
        assertEquals(1, model.respondCallCount)
    }

    @Test
    public fun testManagerAppendsAdditionalContextAfterProviderContext(): Unit = runTest {
        val model = MockModel()
        val manager = testManager()
        manager.setModelResolver { ModelSelector.Custom(model) }
        manager.setContextProvider(
            testUsage,
            itemsProvider(EvaluationContext.Item("provider", priority = 0.0))
        )

        manager.evaluate(
            TestEvaluation(),
            additionalContext = EvaluationContext(
                listOf(EvaluationContext.Item("authored", priority = 5.0))
            )
        )

        assertEquals(
            listOf("provider", "authored"),
            model.lastRequest?.context?.items?.map { it.content }
        )
    }

    @Test
    public fun testDefaultContextProviderIsOnlyAFallback(): Unit = runTest {
        val model = MockModel()
        val manager = testManager()
        manager.setModelResolver { ModelSelector.Custom(model) }
        manager.setDefaultContextProvider {
            EvaluationContext(listOf(EvaluationContext.Item("default")))
        }

        manager.evaluate(TestEvaluation())
        assertEquals(listOf("default"), model.lastRequest?.context?.items?.map { it.content })

        // A usage-specific provider wins outright; the two are never combined.
        manager.setContextProvider(testUsage, itemsProvider(EvaluationContext.Item("specific")))
        manager.evaluate(TestEvaluation())
        assertEquals(listOf("specific"), model.lastRequest?.context?.items?.map { it.content })
    }

    @Test
    public fun testClearingContextProviderFallsBackToDefaultProvider(): Unit = runTest {
        val manager = testManager()
        manager.setDefaultContextProvider {
            EvaluationContext(listOf(EvaluationContext.Item("default")))
        }
        manager.setContextProvider(testUsage, itemsProvider(EvaluationContext.Item("specific")))
        manager.setContextProvider(testUsage, null)

        assertEquals(
            listOf("default"),
            manager.fetchContext(testUsage, Unit).items.map { it.content }
        )
    }

    // MARK: requiresContext

    @Test
    public fun testSkipsContextRequiringEvaluationWhenContextEmpty(): Unit = runTest {
        val model = MockModel()
        val manager = testManager()
        manager.setModelResolver { ModelSelector.Custom(model) }
        // No provider registered, so the resolved context is empty.

        assertTrue(manager.evaluate(ContextRequiredEvaluation()) is EvaluationResult.Skipped)
        assertEquals(0, model.respondCallCount)
    }

    @Test
    public fun testRunsContextRequiringEvaluationWhenProviderSuppliesContext(): Unit = runTest {
        val model = MockModel()
        val manager = testManager()
        manager.setModelResolver { ModelSelector.Custom(model) }
        manager.setContextProvider(testUsage, itemsProvider(EvaluationContext.Item("likes hiking")))

        assertNotNull(manager.evaluate(ContextRequiredEvaluation()).output)
        assertEquals(1, model.respondCallCount)
    }

    @Test
    public fun testRunsContextRequiringEvaluationWhenOnlyAdditionalContextProvided(): Unit = runTest {
        // The gate is on the merged context, so caller-supplied context satisfies it even
        // with no provider registered.
        val model = MockModel()
        val manager = testManager()
        manager.setModelResolver { ModelSelector.Custom(model) }

        val result = manager.evaluate(
            ContextRequiredEvaluation(),
            additionalContext = EvaluationContext(listOf(EvaluationContext.Item("authored")))
        )

        assertNotNull(result.output)
        assertEquals(1, model.respondCallCount)
    }

    @Test
    public fun testUncappedZeroDelayRetryStillHitsTheCeiling() {
        // An app policy that never caps and never delays leaves the loop with no suspension
        // point of its own. On a single thread that starves the ceiling's timer and spins
        // forever, so the retry path yields when the delay is zero. Real time and a real
        // single-threaded dispatcher, since a virtual clock only advances on a delay.
        var attempts = 0
        val model = object : ModelAdapter {
            override fun retryDecision(usage: Usage<*>, error: Throwable, attempt: Int) =
                RetryDecision.Retry(Duration.ZERO)

            override suspend fun respond(request: ModelRequest): JsonValue {
                attempts += 1
                throw SampleError()
            }
        }

        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = runBlocking(executor.asCoroutineDispatcher()) {
                testEvaluator(maxResponseTimeout = 100.milliseconds)
                    .evaluate(TestEvaluation(), model, EvaluationContext.EMPTY)
            }

            assertTrue(result is EvaluationResult.Failed)
            assertTrue(attempts > 1)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    public fun testNaNPriorityDoesNotBreakTrimming() {
        // priority is a public Double, so an app can hand us a divide-by-zero result.
        val context = EvaluationContext(
            listOf(
                EvaluationContext.Item("keep", priority = 0.0),
                EvaluationContext.Item("nan", priority = Double.NaN)
            )
        )

        val (trimmed, dropped) = requireNotNull(context.droppingLowestPriorityItem())

        // Double ordering puts NaN above every number, so it is the least important.
        assertEquals("nan", dropped.content)
        assertEquals(listOf("keep"), trimmed.items.map { it.content })
    }

    @Test
    public fun testThrowingModelResolverSkipsRatherThanEscaping(): Unit = runTest {
        // evaluate is documented to fail open; app code in the resolver must not break that.
        val manager = testManager()
        manager.setModelResolver { throw SampleError() }

        assertTrue(manager.evaluate(TestEvaluation()) is EvaluationResult.Skipped)
    }

    @Test
    public fun testThrowingModelFactorySkipsRatherThanEscaping(): Unit = runTest {
        val manager = testManager()
        manager.registerModelFactory { throw SampleError() }

        assertTrue(manager.evaluate(TestEvaluation()) is EvaluationResult.Skipped)
    }

    @Test
    public fun testThrowingAvailabilityGetterIsTreatedAsUnavailable(): Unit = runTest {
        val model = object : ModelAdapter {
            override val availability: ModelAvailability get() = throw SampleError()
            override suspend fun respond(request: ModelRequest) = allowResponse()
        }

        assertTrue(eval(model) is EvaluationResult.Skipped)
    }

    @Test
    public fun testRecordExposesDurationToJavaCallers(): Unit = runTest {
        // EvaluationObserver is Java-implementable, but Duration's accessor is name-mangled.
        val observer = RecordingObserver()
        testEvaluator().evaluate(TestEvaluation(), MockModel(), EvaluationContext.EMPTY, observer)

        val record = observer.records.first()
        assertEquals(record.duration.inWholeMilliseconds, record.durationMillis)
    }

    // MARK: context providers

    @Test
    public fun testProviderFailureDegradesToEmptyContext(): Unit = runTest {
        // The provider is app code on a display path; a throw must not take the display down.
        val manager = testManager()
        manager.setContextProvider(testUsage) { throw SampleError() }

        assertEquals(EvaluationContext.EMPTY, manager.fetchContext(testUsage, Unit))
    }

    @Test
    public fun testProviderSubjectTypeMismatchDegradesToEmptyContext(): Unit = runTest {
        // Two features sharing a usage key with different subject types.
        val manager = testManager()
        val stringUsage = Usage<String>(testUsage.rawValue)
        manager.setContextProvider(stringUsage) { subject ->
            EvaluationContext(listOf(EvaluationContext.Item(subject)))
        }

        assertEquals(EvaluationContext.EMPTY, manager.fetchContext(testUsage, Unit))
    }

    // MARK: per-usage model resolution

    @Test
    public fun testDefaultModelFactoryIsInvokedOnce() {
        // A factory that really constructs a backend must not run per evaluation.
        var invocations = 0
        val manager = testManager()
        manager.registerModelFactory {
            invocations += 1
            MockModel()
        }

        val first = manager.model(testUsage)
        val second = manager.model(testUsage)

        assertEquals(1, invocations)
        assertSame(first, second)
        assertSame(first, manager.defaultModel)
    }

    @Test
    public fun testModelIsNullWhenNoneConfigured() {
        assertNull(testManager().model(Usage<Unit>("test")))
    }

    @Test
    public fun testModelReturnsDefaultFactoryModelWhenNoResolver() {
        val model = MockModel()
        val manager = testManager()
        manager.registerModelFactory { model }

        assertSame(model, manager.model(Usage<Unit>("any_usage")))
        assertSame(model, manager.defaultModel)
    }

    @Test
    public fun testResolverWinsOverDefaultFactory() {
        val defaultModel = MockModel()
        val usageModel = MockModel()
        val manager = testManager()
        manager.registerModelFactory { defaultModel }
        manager.setModelResolver { usage ->
            if (usage == testUsage) {
                ModelSelector.Custom(usageModel)
            } else {
                ModelSelector.DefaultModel
            }
        }

        assertSame(usageModel, manager.model(testUsage))
        assertSame(defaultModel, manager.model(Usage<Unit>("other")))
    }

    @Test
    public fun testClearingResolverFallsBackToDefaultFactory() {
        val defaultModel = MockModel()
        val manager = testManager()
        manager.registerModelFactory { defaultModel }
        manager.setModelResolver { ModelSelector.Custom(MockModel()) }
        manager.setModelResolver(null)

        assertSame(defaultModel, manager.model(testUsage))
    }

    // MARK: privacy manager gating

    @Test
    public fun testModelIsNullWhenAIDisabled() {
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.NONE))
        manager.registerModelFactory { MockModel() }

        assertNull(manager.model(testUsage))
        assertNull(manager.defaultModel)
    }

    @Test
    public fun testModelIsResolvedWhenAIEnabled() {
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.ON_DEVICE_AI))
        manager.registerModelFactory { MockModel() }

        assertEquals(ModelAvailability.Available, manager.model(testUsage)?.availability)
        assertEquals(ModelAvailability.Available, manager.defaultModel?.availability)
    }

    @Test
    public fun testGatedModelReportsNotEnabledWhenAIDisabled() {
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.NONE))
        manager.registerModelFactory { MockModel() }

        // Unlike model(), gatedModel() still returns an instance — just one reporting
        // NotEnabled — so a caller holding onto it sees the gate reflected in its
        // availability rather than losing the reference outright.
        assertEquals(
            ModelAvailability.Unavailable(ModelAvailability.Reason.NotEnabled),
            manager.gatedModel(testUsage)?.availability
        )
    }

    @Test
    public fun testGatedModelStaysInSyncAsPrivacyManagerToggles() {
        val privacyManager = testPrivacyManager()
        val manager = testManager(privacyManager)
        manager.registerModelFactory { MockModel() }

        // A single resolved reference, held across the toggle — mirrors a caller that
        // resolves once and caches the result.
        val resolved = manager.gatedModel(testUsage)
        assertEquals(ModelAvailability.Available, resolved?.availability)

        privacyManager.disable(PrivacyManager.Feature.ON_DEVICE_AI)
        assertEquals(
            ModelAvailability.Unavailable(ModelAvailability.Reason.NotEnabled),
            resolved?.availability
        )

        privacyManager.enable(PrivacyManager.Feature.ON_DEVICE_AI)
        assertEquals(ModelAvailability.Available, resolved?.availability)
    }

    @Test
    public fun testGatedModelAvailabilityUpdatesEmitsOnPrivacyManagerChange(): Unit = runTest {
        val privacyManager = testPrivacyManager()
        val manager = testManager(privacyManager)
        manager.registerModelFactory { MockModel() }

        val resolved = requireNotNull(manager.gatedModel(testUsage))

        resolved.availabilityUpdates.test {
            assertEquals(ModelAvailability.Available, awaitItem())

            privacyManager.disable(PrivacyManager.Feature.ON_DEVICE_AI)
            assertEquals(
                ModelAvailability.Unavailable(ModelAvailability.Reason.NotEnabled),
                awaitItem()
            )

            privacyManager.enable(PrivacyManager.Feature.ON_DEVICE_AI)
            assertEquals(ModelAvailability.Available, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun testEvaluateSkipsWhenAIDisabled(): Unit = runTest {
        val model = MockModel()
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.NONE))
        manager.setModelResolver { ModelSelector.Custom(model) }

        assertTrue(manager.evaluate(TestEvaluation()) is EvaluationResult.Skipped)
        assertEquals(0, model.respondCallCount)
    }

    @Test
    public fun testFetchContextReturnsEmptyWhenAIDisabled(): Unit = runTest {
        val manager = testManager(testPrivacyManager(PrivacyManager.Feature.NONE))
        manager.setContextProvider(testUsage, itemsProvider(EvaluationContext.Item("likes hiking")))

        assertEquals(EvaluationContext.EMPTY, manager.fetchContext(testUsage, Unit))
    }
}
