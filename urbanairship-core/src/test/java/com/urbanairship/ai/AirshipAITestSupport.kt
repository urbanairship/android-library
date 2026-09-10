/* Copyright Airship and Contributors */
package com.urbanairship.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PrivacyManager
import com.urbanairship.json.AirshipJsonSchema
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

internal val testUsage: Usage<Unit> = Usage("test_usage")

internal val testSchema: AirshipJsonSchema = AirshipJsonSchema.obj(
    properties = mapOf(
        "allow" to AirshipJsonSchema.boolean(description = "whether to allow"),
        "reason" to AirshipJsonSchema.string()
    ),
    required = listOf("allow", "reason")
)

internal fun allowResponse(allow: Boolean = true, reason: String = "ok"): JsonValue =
    jsonMapOf("allow" to allow, "reason" to reason).toJsonValue()

internal fun offSchemaResponse(): JsonValue =
    jsonMapOf("unexpected_key" to "value").toJsonValue()

internal data class TestOutput(val allow: Boolean, val reason: String)

internal open class TestEvaluation : Evaluation<TestOutput, Unit> {
    override val usage: Usage<Unit> = testUsage
    override val subject: Unit = Unit
    override val schema: AirshipJsonSchema = testSchema

    override fun instructions(): String = "rules"

    override fun prompt(context: EvaluationContext): String = "subject"

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
internal class MockModel(
    availability: ModelAvailability = ModelAvailability.Available,
    response: () -> JsonValue = { allowResponse() },
    var maxAttempts: Int = 1
) : ModelAdapter {

    var availabilityValue: ModelAvailability = availability
    var responses: MutableList<() -> JsonValue> = mutableListOf(response)
    var respondDelay: Duration = Duration.ZERO
    var retryDelay: Duration = Duration.ZERO

    /** `null` falls through to the framework default. */
    var retryDecision: ((Throwable, Int) -> RetryDecision)? = { _, attempt ->
        if (attempt < maxAttempts) RetryDecision.Retry(retryDelay) else RetryDecision.Fail
    }

    var respondCallCount: Int = 0
        private set

    var lastRequest: ModelRequest? = null
        private set

    val retryErrors: MutableList<Throwable> = mutableListOf()

    override val availability: ModelAvailability
        get() = availabilityValue

    override fun retryDecision(
        usage: Usage<*>,
        error: Throwable,
        attempt: Int
    ): RetryDecision {
        retryErrors.add(error)
        return retryDecision?.invoke(error, attempt)
            ?: RetryDecision.defaultBackoff(error, attempt)
    }

    override suspend fun respond(request: ModelRequest): JsonValue {
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
    vararg items: EvaluationContext.Item
): EvaluationContextProvider<Unit> = EvaluationContextProvider { EvaluationContext(items.toList()) }

/** Collects the records handed to an observer. */
internal class RecordingObserver : EvaluationObserver {
    val records: MutableList<EvaluationRecord> = mutableListOf()
    override fun onEvaluation(record: EvaluationRecord) {
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
    maxResponseTimeout: Duration = Evaluator.DEFAULT_MAX_RESPONSE_TIMEOUT
): Evaluator = Evaluator(
    maxResponseTimeout = maxResponseTimeout,
    observerScope = CoroutineScope(UnconfinedTestDispatcher())
)

/**
 * A manager with AI enabled by default — the privacy gate itself is covered separately.
 */
internal fun testManager(
    privacyManager: PrivacyManager = testPrivacyManager(),
    evaluator: Evaluator = testEvaluator()
): DefaultAirshipAi = DefaultAirshipAi(privacyManager = privacyManager, evaluator = evaluator)
