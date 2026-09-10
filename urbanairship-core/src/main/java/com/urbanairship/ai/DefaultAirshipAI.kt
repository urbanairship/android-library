/* Copyright Airship and Contributors */
package com.urbanairship.ai

import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Default [InternalAirshipAi].
 *
 * Gated by [PrivacyManager.Feature.ON_DEVICE_AI] — disabled, evaluations and context fetches
 * behave as though no model were ever registered, and nothing is reported to the observer.
 * [gatedModel] is the one exception: it resolves either way, so a caller holding the result sees
 * the gate through the model's availability rather than losing the reference.
 */
internal class DefaultAirshipAi(
    private val privacyManager: PrivacyManager,
    private val evaluator: Evaluator = Evaluator()
) : InternalAirshipAi {

    private val providerRegistry = ProviderRegistry()

    @Volatile
    private var modelResolver: ModelResolver? = null

    /**
     * Wrapped in `lazy` so the registered factory runs at most once — a factory that really
     * builds a backend would otherwise allocate one per evaluation.
     */
    @Volatile
    private var builtInModel: Lazy<ModelAdapter>? = null

    @Volatile
    private var evaluationObserver: EvaluationObserver? = null

    private val enabled: Boolean
        get() = privacyManager.isEnabled(PrivacyManager.Feature.ON_DEVICE_AI)

    override val defaultModel: ModelAdapter?
        get() = if (enabled) builtInModel?.value else null

    override fun model(usage: Usage<*>): ModelAdapter? =
        if (enabled) resolveModel(usage) else null

    override fun gatedModel(usage: Usage<*>): ModelAdapter? =
        resolveModel(usage)?.let { PrivacyGatedModel(it, privacyManager) }

    override fun <Subject> setContextProvider(
        usage: Usage<Subject>,
        provider: EvaluationContextProvider<Subject>?
    ) {
        providerRegistry.setContextProvider(usage, provider)
    }

    override fun setDefaultContextProvider(provider: DefaultEvaluationContextProvider?) {
        providerRegistry.setDefaultContextProvider(provider)
    }

    override fun setEvaluationObserver(observer: EvaluationObserver?) {
        evaluationObserver = observer
    }

    override fun setModelResolver(resolver: ModelResolver?) {
        modelResolver = resolver
    }

    override fun registerModelFactory(factory: () -> ModelAdapter) {
        builtInModel = lazy(factory)
    }

    override suspend fun <Subject> fetchContext(
        usage: Usage<Subject>,
        subject: Subject
    ): EvaluationContext {
        if (!enabled) {
            return EvaluationContext.EMPTY
        }
        return providerRegistry.fetchContext(usage.rawValue, subject)
    }

    override suspend fun <Output, Subject> evaluate(
        evaluation: Evaluation<Output, Subject>,
        additionalContext: EvaluationContext
    ): EvaluationResult<Output> {
        // The privacy gate turns the whole feature off, observer included — an app that opted
        // out doesn't need a record per evaluation telling it so.
        if (!enabled) {
            return EvaluationResult.Skipped(AI_DISABLED)
        }

        // Snapshotted before the provider runs so one evaluation can't report to an observer
        // that was replaced mid-flight.
        val observer = evaluationObserver

        // The resolver and the model factory are both app code, and evaluate is documented to
        // fail open, so a throw from either skips rather than reaching the feature.
        val model = try {
            model(evaluation.usage)
        } catch (e: Exception) {
            UALog.e(e) { "AI model resolution failed for ${evaluation.usage.rawValue}" }
            null
        }

        if (model == null) {
            evaluator.reportSkipped(evaluation, EvaluationContext.EMPTY, NO_MODEL, observer)
            return EvaluationResult.Skipped(NO_MODEL)
        }

        // Provider context first, then the caller's additional context appended after (later
        // items win priority ties when the model trims to fit its window).
        val merged = providerRegistry
            .fetchContext(evaluation.usage.rawValue, evaluation.subject)
            .appending(additionalContext)

        // With no context an opted-in evaluation would guess from the prompt alone, so skip
        // and let the caller fall back. Most evaluations opt out and run regardless.
        if (evaluation.requiresContext && merged.items.isEmpty()) {
            evaluator.reportSkipped(evaluation, merged, NO_CONTEXT, observer)
            return EvaluationResult.Skipped(NO_CONTEXT)
        }

        return evaluator.evaluate(
            evaluation = evaluation,
            model = model,
            context = merged,
            observer = observer
        )
    }

    private fun resolveModel(usage: Usage<*>): ModelAdapter? {
        val selector = modelResolver?.resolve(usage) ?: ModelSelector.DefaultModel
        return when (selector) {
            ModelSelector.DefaultModel -> builtInModel?.value
            is ModelSelector.Custom -> selector.model
        }
    }

    private companion object {
        const val AI_DISABLED = "AI disabled by privacy manager"
        const val NO_MODEL = "No model configured"
        const val NO_CONTEXT = "No context to personalize on"
    }
}

/**
 * A resolved model wrapped so its availability reflects [PrivacyManager.Feature.ON_DEVICE_AI] in
 * addition to the underlying model's own state.
 *
 * See [InternalAirshipAi.gatedModel].
 */
private class PrivacyGatedModel(
    private val wrapped: ModelAdapter,
    private val privacyManager: PrivacyManager
) : ModelAdapter {

    private fun gate(availability: ModelAvailability): ModelAvailability =
        if (privacyManager.isEnabled(PrivacyManager.Feature.ON_DEVICE_AI)) {
            availability
        } else {
            ModelAvailability.Unavailable(ModelAvailability.Reason.NotEnabled)
        }

    override val availability: ModelAvailability
        get() = gate(wrapped.availability)

    override val availabilityUpdates: Flow<ModelAvailability>
        get() = merge(
            wrapped.availabilityUpdates.map(::gate),
            // Only a change signal — the wrapped model's availability is re-read, not carried.
            privacyManager.featureUpdates.map { gate(wrapped.availability) }
        )
            .onStart { emit(gate(wrapped.availability)) }
            .distinctUntilChanged()

    override fun retryDecision(
        usage: Usage<*>,
        error: Throwable,
        attempt: Int
    ): RetryDecision = wrapped.retryDecision(usage, error, attempt)

    override suspend fun respond(request: ModelRequest): JsonValue = wrapped.respond(request)
}
