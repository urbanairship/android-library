/* Copyright Airship and Contributors */
package com.urbanairship.ai

import androidx.annotation.RestrictTo

/**
 * Entry point for Airship's on-device AI features, reached via [com.urbanairship.Airship.ai].
 *
 * Gated by [com.urbanairship.PrivacyManager.Feature.ON_DEVICE_AI] — disabled, no model resolves
 * and every evaluation is skipped.
 */
public interface AirshipAI {

    /**
     * Registers the context provider for a usage.
     *
     * ```
     * val usage = Usage<MyMessage>("my_feature")
     * Airship.ai.setContextProvider(usage) { message ->
     *     EvaluationContext(listOf(EvaluationContext.Item("Last booked: ${store.lastBooking}")))
     * }
     * ```
     *
     * @param usage The usage to provide context for.
     * @param provider The provider, or `null` to clear it.
     */
    public fun <Subject> setContextProvider(
        usage: Usage<Subject>,
        provider: ContextProvider<Subject>?
    )

    /**
     * Registers a fallback provider for usages with no provider of their own.
     *
     * A usage-specific provider wins outright; the two are never combined.
     *
     * @param provider The provider, or `null` to clear it.
     */
    public fun setDefaultContextProvider(provider: DefaultContextProvider?)

    /**
     * Registers an observer called once per evaluation, whatever the outcome — including
     * evaluations skipped before a model was consulted, such as when none is configured.
     *
     * Nothing fires while [com.urbanairship.PrivacyManager.Feature.ON_DEVICE_AI] is disabled;
     * the gate turns the feature off, observer included.
     *
     * Per *evaluation*, not per display: a feature that re-evaluates produces a record each
     * time, so dedupe before treating these as impressions. Airship reports none of this itself.
     *
     * @param observer The observer, or `null` to clear it.
     */
    public fun setEvaluationObserver(observer: EvaluationObserver?)

    /**
     * Registers a resolver that routes each usage to a model backend.
     *
     * ```
     * Airship.ai.setModelResolver { usage ->
     *     if (usage == myUsage) ModelSelector.Custom(myModel)
     *     else ModelSelector.DefaultModel
     * }
     * ```
     *
     * @param resolver The resolver, or `null` to clear it, reverting every usage to the SDK
     * default.
     */
    public fun setModelResolver(resolver: ModelResolver?)

    /**
     * The SDK's built-in default model, or `null` when none is registered.
     *
     * Read it inside an [ModelResolver] to fall back to another model only when the built-in
     * one is unavailable.
     */
    public val defaultModel: Model?

    /**
     * Returns the model resolved for [usage], or `null` when none is configured.
     *
     * The resolver set via [setModelResolver] wins over the SDK default.
     *
     * @param usage The usage to resolve a model for.
     * @return The resolved model, or `null`.
     */
    public fun model(usage: Usage<*>): Model?
}

/**
 * Internal surface extending [AirshipAI] with evaluation and model registration, so feature
 * modules can be tested with a mock.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface InternalAirshipAI : AirshipAI {

    /**
     * Runs an evaluation through the active model, failing open to [EvaluationResult.Skipped]
     * when no model is available.
     *
     * @param evaluation The evaluation to run.
     * @param additionalContext Feature-authored context appended after the provider's, so it
     * wins priority ties when the model trims.
     * @return The result.
     */
    public suspend fun <Output, Subject> evaluate(
        evaluation: Evaluation<Output, Subject>,
        additionalContext: EvaluationContext = EvaluationContext.EMPTY
    ): EvaluationResult<Output>

    /**
     * Registers the SDK's built-in default model, replacing the current one.
     *
     * @param factory Returns the model to use. Invoked at most once, on first resolution.
     */
    public fun registerModelFactory(factory: () -> Model)

    /**
     * Fetches the registered provider's context for a usage, or [EvaluationContext.EMPTY] when none is
     * registered. Excludes the additional context an evaluation may append.
     *
     * @param usage The usage.
     * @param subject The feature-specific subject.
     * @return The context.
     */
    public suspend fun <Subject> fetchContext(usage: Usage<Subject>, subject: Subject): EvaluationContext

    /**
     * Like [model], but wrapped so its availability tracks
     * [com.urbanairship.PrivacyManager.Feature.ON_DEVICE_AI] too — for a caller that resolves
     * once and holds the result rather than re-resolving. [model] still returns the exact
     * registered instance.
     *
     * @param usage The usage to resolve a model for.
     * @return The resolved model, or `null`.
     */
    public fun gatedModel(usage: Usage<*>): Model?
}
