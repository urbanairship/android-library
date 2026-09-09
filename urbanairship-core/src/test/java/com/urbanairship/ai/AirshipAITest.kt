/* Copyright Airship and Contributors */
package com.urbanairship.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.urbanairship.PrivacyManager
import com.urbanairship.json.jsonMapOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        evaluation: TestEvaluation = TestEvaluation()
    ): AIEvaluationResult<TestOutput> =
        AIEvaluator().evaluate(evaluation, model, context)

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
        val model = MockAIModel(response = { jsonMapOf("unexpected_key" to "value").toJsonValue() })
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
            { jsonMapOf("unexpected_key" to "value").toJsonValue() },
            { allowResponse(allow = true, reason = "second try") }
        )

        val result = eval(model)

        assertEquals(TestOutput(allow = true, reason = "second try"), result.output)
        assertEquals(2, model.respondCallCount)
    }

    @Test
    public fun testFailsAfterExhaustingAttempts(): Unit = runTest {
        val model = MockAIModel(
            response = { jsonMapOf("unexpected_key" to "value").toJsonValue() },
            maxAttempts = 2
        )

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
    public fun testRetriesUpToMaxAttemptsOnOrdinaryFailure(): Unit = runTest {
        val model = MockAIModel(response = { throw SampleError() }, maxAttempts = 3)

        assertTrue(eval(model) is AIEvaluationResult.Failed)
        assertEquals(3, model.respondCallCount)
    }

    @Test
    public fun testTimeoutTerminatesSlowModel(): Unit = runTest {
        val model = MockAIModel(responseTimeout = 100.milliseconds)
        model.respondDelay = 60.seconds

        assertTrue(eval(model) is AIEvaluationResult.Failed)
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

    // MARK: per-usage model resolution

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
