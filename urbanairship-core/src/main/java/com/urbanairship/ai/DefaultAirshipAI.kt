/* Copyright Airship and Contributors */
package com.urbanairship.ai

import com.urbanairship.PrivacyManager
import com.urbanairship.json.JsonValue
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Default [InternalAirshipAI].
 *
 * Gated by [PrivacyManager.Feature.ON_DEVICE_AI] — disabled, evaluations and context fetches
 * behave as though no model were ever registered.
 */
internal class DefaultAirshipAI(
    private val privacyManager: PrivacyManager,
    private val evaluator: AIEvaluator = AIEvaluator()
) : InternalAirshipAI {

    private val providerRegistry = AIContextProviderRegistry()

    @Volatile
    private var modelResolver: AIModelResolver? = null

    @Volatile
    private var defaultModelFactory: (() -> AIModel)? = null

    @Volatile
    private var evaluationObserver: AIEvaluationObserver? = null

    private val enabled: Boolean
        get() = privacyManager.isEnabled(PrivacyManager.Feature.ON_DEVICE_AI)

    override val defaultModel: AIModel?
        get() = if (enabled) defaultModelFactory?.invoke() else null

    override fun model(usage: AIUsage<*>): AIModel? =
        if (enabled) resolveModel(usage) else null

    override fun gatedModel(usage: AIUsage<*>): AIModel? =
        resolveModel(usage)?.let { PrivacyGatedModel(it, privacyManager) }

    override fun <Subject> setContextProvider(
        usage: AIUsage<Subject>,
        provider: AIContextProvider<Subject>?
    ) {
        providerRegistry.setContextProvider(usage, provider)
    }

    override fun setDefaultContextProvider(provider: AIDefaultContextProvider?) {
        providerRegistry.setDefaultContextProvider(provider)
    }

    override fun setEvaluationObserver(observer: AIEvaluationObserver?) {
        evaluationObserver = observer
    }

    override fun setModelResolver(resolver: AIModelResolver?) {
        modelResolver = resolver
    }

    override fun registerModelFactory(factory: () -> AIModel) {
        defaultModelFactory = factory
    }

    override suspend fun <Subject> fetchContext(
        usage: AIUsage<Subject>,
        subject: Subject
    ): AIContext {
        if (!enabled) {
            return AIContext.EMPTY
        }
        return providerRegistry.fetchContext(usage.rawValue, subject)
    }

    override suspend fun <Output, Subject> evaluate(
        evaluation: AIEvaluation<Output, Subject>,
        additionalContext: AIContext
    ): AIEvaluationResult<Output> {
        if (!enabled) {
            return AIEvaluationResult.Skipped("AI disabled by privacy manager")
        }

        val model = model(evaluation.usage)
            ?: return AIEvaluationResult.Skipped("No model configured")

        // Snapshotted before the provider runs so one evaluation can't report to an observer
        // that was replaced mid-flight.
        val observer = evaluationObserver

        // Provider context first, then the caller's additional context appended after (later
        // items win priority ties when the model trims to fit its window).
        val merged = providerRegistry
            .fetchContext(evaluation.usage.rawValue, evaluation.subject)
            .appending(additionalContext)

        // With no context an opted-in evaluation would guess from the prompt alone, so skip
        // and let the caller fall back. Most evaluations opt out and run regardless.
        if (evaluation.requiresContext && merged.items.isEmpty()) {
            return AIEvaluationResult.Skipped("No context to personalize on")
        }

        return evaluator.evaluate(
            evaluation = evaluation,
            model = model,
            context = merged,
            observer = observer
        )
    }

    private fun resolveModel(usage: AIUsage<*>): AIModel? {
        val selector = modelResolver?.resolve(usage) ?: AIModelSelector.DefaultModel
        return when (selector) {
            AIModelSelector.DefaultModel -> defaultModelFactory?.invoke()
            is AIModelSelector.Custom -> selector.model
        }
    }
}

/**
 * A resolved model wrapped so its availability reflects [PrivacyManager.Feature.ON_DEVICE_AI] in
 * addition to the underlying model's own state.
 *
 * See [InternalAirshipAI.gatedModel].
 */
private class PrivacyGatedModel(
    private val wrapped: AIModel,
    private val privacyManager: PrivacyManager
) : AIModel {

    private fun gate(availability: AIModelAvailability): AIModelAvailability =
        if (privacyManager.isEnabled(PrivacyManager.Feature.ON_DEVICE_AI)) {
            availability
        } else {
            AIModelAvailability.Unavailable(AIModelAvailability.Reason.NotEnabled)
        }

    override val availability: AIModelAvailability
        get() = gate(wrapped.availability)

    override val availabilityUpdates: Flow<AIModelAvailability>
        get() = merge(
            wrapped.availabilityUpdates.map(::gate),
            // Only a change signal — the wrapped model's availability is re-read, not carried.
            privacyManager.featureUpdates.map { gate(wrapped.availability) }
        )
            .onStart { emit(gate(wrapped.availability)) }
            .distinctUntilChanged()

    override val maxAttempts: Int
        get() = wrapped.maxAttempts

    override val responseTimeout: Duration
        get() = wrapped.responseTimeout

    override suspend fun respond(request: AIModelRequest): JsonValue = wrapped.respond(request)
}
