/* Copyright Airship and Contributors */
package com.urbanairship.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PrivacyManager
import com.urbanairship.json.JsonSchema
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.preferences.PreferenceStore
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher

internal class SampleError : Exception("boom")

internal val testUsage: AIUsage<Unit> = AIUsage("test_usage")

internal val testSchema: JsonSchema = JsonSchema.obj(
    properties = mapOf(
        "allow" to JsonSchema.boolean(description = "whether to allow"),
        "reason" to JsonSchema.string()
    ),
    required = listOf("allow", "reason")
)

internal fun allowResponse(allow: Boolean = true, reason: String = "ok"): JsonValue =
    jsonMapOf("allow" to allow, "reason" to reason).toJsonValue()

internal fun offSchemaResponse(): JsonValue =
    jsonMapOf("unexpected_key" to "value").toJsonValue()

internal data class TestOutput(val allow: Boolean, val reason: String)

internal open class TestEvaluation : AIEvaluation<TestOutput, Unit> {
    override val usage: AIUsage<Unit> = testUsage
    override val subject: Unit = Unit
    override val schema: JsonSchema = testSchema

    override fun instructions(): String = "rules"

    override fun prompt(context: AIContext): String = "subject"

    override fun parseOutput(json: JsonValue): TestOutput {
        val map = json.requireMap()
        return TestOutput(allow = map.requireField("allow"), reason = map.requireField("reason"))
    }
}

/** Like [TestEvaluation] but opts into `requiresContext`. */
internal class ContextRequiredEvaluation : TestEvaluation() {
    override val requiresContext: Boolean = true
}

/**
 * Records what it was asked and returns canned responses — one per attempt when [responses]
 * holds several, repeating the last.
 *
 * [maxAttempts] drives the stub [retryDecision]: it retries after [retryDelay] until that many
 * attempts have failed. Set [retryDecision] to `null` to exercise the framework default instead.
 */
internal class MockAIModel(
    availability: AIModelAvailability = AIModelAvailability.Available,
    response: () -> JsonValue = { allowResponse() },
    var maxAttempts: Int = 1
) : AIModel {

    var availabilityValue: AIModelAvailability = availability
    var responses: MutableList<() -> JsonValue> = mutableListOf(response)
    var respondDelay: Duration = Duration.ZERO
    var retryDelay: Duration = Duration.ZERO

    /** `null` falls through to the framework default. */
    var retryDecision: ((Throwable, Int) -> AIRetryDecision)? = { _, attempt ->
        if (attempt < maxAttempts) AIRetryDecision.Retry(retryDelay) else AIRetryDecision.Fail
    }

    var respondCallCount: Int = 0
        private set

    var lastRequest: AIModelRequest? = null
        private set

    val retryErrors: MutableList<Throwable> = mutableListOf()

    override val availability: AIModelAvailability
        get() = availabilityValue

    override fun retryDecision(
        usage: AIUsage<*>,
        error: Throwable,
        attempt: Int
    ): AIRetryDecision {
        retryErrors.add(error)
        return retryDecision?.invoke(error, attempt)
            ?: AIRetryDecision.defaultBackoff(error, attempt)
    }

    override suspend fun respond(request: AIModelRequest): JsonValue {
        respondCallCount += 1
        lastRequest = request

        if (respondDelay > Duration.ZERO) {
            delay(respondDelay)
        }

        val response = if (responses.size > 1) responses.removeAt(0) else responses[0]
        return response()
    }
}

/** Context provider returning a fixed set of items. */
internal fun itemsProvider(
    vararg items: AIContext.Item
): AIContextProvider<Unit> = AIContextProvider { AIContext(items.toList()) }

/** Collects the records handed to an observer. */
internal class RecordingObserver : AIEvaluationObserver {
    val records: MutableList<AIEvaluationRecord> = mutableListOf()
    override fun onEvaluation(record: AIEvaluationRecord) {
        records.add(record)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun testPrivacyManager(
    enabledFeatures: PrivacyManager.Feature = PrivacyManager.Feature.ALL,
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()
): PrivacyManager {
    val context: Context = ApplicationProvider.getApplicationContext()
    return PrivacyManager(
        dataStore = PreferenceStore.inMemoryStore(context),
        defaultEnabledFeatures = enabledFeatures,
        dispatcher = dispatcher
    )
}

/** The observer scope is unconfined so a report lands before the assertion that reads it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun testEvaluator(
    maxResponseTimeout: Duration = AIEvaluator.DEFAULT_MAX_RESPONSE_TIMEOUT
): AIEvaluator = AIEvaluator(
    maxResponseTimeout = maxResponseTimeout,
    observerScope = CoroutineScope(UnconfinedTestDispatcher())
)

/**
 * A manager with AI enabled by default — the privacy gate itself is covered separately.
 */
internal fun testManager(
    privacyManager: PrivacyManager = testPrivacyManager(),
    evaluator: AIEvaluator = testEvaluator()
): DefaultAirshipAI = DefaultAirshipAI(privacyManager = privacyManager, evaluator = evaluator)
