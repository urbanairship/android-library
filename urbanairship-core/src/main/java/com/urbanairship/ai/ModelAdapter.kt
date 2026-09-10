/* Copyright Airship and Contributors */
package com.urbanairship.ai

import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The framework's interface to a model backend.
 *
 * Answers one request; retry and output validation live in the framework, with the retry
 * schedule tuned through [retryDecision].
 *
 * Implementing this needs Kotlin — [respond] is a suspend function.
 */
public interface ModelAdapter {

    /**
     * Whether the model can be used right now, and if not, why.
     *
     * Read fresh before each evaluation, which is skipped when this is
     * [ModelAvailability.Unavailable]. Override only for a backend that can genuinely be
     * unusable; otherwise let a failure surface from [respond].
     */
    public val availability: ModelAvailability
        get() = ModelAvailability.Available

    /**
     * Emits whenever [availability] changes.
     *
     * The default emits the current value once and completes, which is right for a model whose
     * availability never changes. Treat emissions as change notifications and read
     * [availability] for the value right now.
     */
    public val availabilityUpdates: Flow<ModelAvailability>
        get() = flowOf(availability)

    /**
     * Decides what happens after a failed attempt.
     *
     * Called each time [respond] throws or the response fails schema validation (as an
     * [SchemaValidationException]). There is no separate attempt cap — cap it yourself by
     * returning [RetryDecision.Fail] once [attempt] says to stop. The framework enforces its
     * own wall-clock ceiling on the whole loop regardless of what this returns.
     *
     * Defaults to [RetryDecision.defaultBackoff]: a schema mismatch retries immediately, any
     * other error backs off 1s then 4s, failing after three attempts. Override for a backend
     * where a retry is expensive or slow, or one that wants a different schedule.
     *
     * @param usage Which feature's evaluation this is.
     * @param error The error from the attempt that just failed.
     * @param attempt The attempt number that just failed, starting at 1.
     * @return Whether to retry, and after how long.
     */
    public fun retryDecision(
        usage: Usage<*>,
        error: Throwable,
        attempt: Int
    ): RetryDecision = RetryDecision.defaultBackoff(error, attempt)

    /**
     * Answers a single request.
     *
     * At minimum, send [ModelRequest.prompt]. If the prompt exceeds your input limit, shrink
     * it with [ModelRequest.droppingLowestPriorityContextItem].
     *
     * @param request The request to answer.
     * @return The model's structured response.
     */
    public suspend fun respond(request: ModelRequest): JsonValue
}

/** Whether a resolved model can be used right now. */
public sealed class ModelAvailability {

    /** The model can be used. */
    public data object Available : ModelAvailability()

    /**
     * The model can't be used.
     *
     * @param reason Why not.
     */
    public data class Unavailable public constructor(
        public val reason: Reason
    ) : ModelAvailability()

    /** Why a model can't be used — outcomes an app can act on, not backend internals. */
    public sealed class Reason {
        /** Ineligible hardware or an unsupported OS version. */
        public data object DeviceNotEligible : Reason()

        /** No usable model: not downloaded, still preparing, or not registered. */
        public data object MissingModel : Reason()

        /** AI features are turned off, by the user or by configuration. */
        public data object NotEnabled : Reason()

        /**
         * Any other reason.
         *
         * @param description A human-readable description.
         */
        public data class Other public constructor(public val description: String) : Reason()
    }
}

/**
 * A single request handed to an [ModelAdapter]: instructions, output schema, and prioritized context.
 *
 * The originating evaluation owns how context is labeled and laid out — a model only renders it
 * via [prompt] and, if its input window is tight, trims. The framework builds one per evaluation
 * and hands it to [ModelAdapter.respond]; the constructor is public only so an app can build one to
 * unit-test its own [EvaluationObserver].
 */
public class ModelRequest public constructor(

    /** The system instructions: the model's role and rules. */
    public val instructions: String,

    /** The contract the response must conform to. */
    public val schema: AirshipJsonSchema,

    /** The prioritized context for this request. */
    public val context: EvaluationContext,

    /** Renders [context] into prompt text. Owned by the evaluation, not the model. */
    private val render: (EvaluationContext) -> String
) {

    /**
     * Renders the full prompt for the current context.
     *
     * @return The prompt.
     */
    public fun prompt(): String = render(context)

    /**
     * Returns a copy without its least-important context item, plus the item dropped.
     *
     * Re-read [prompt] after each drop until the request fits:
     * ```
     * var request = request
     * while (tooLong(request.prompt())) {
     *     request = request.droppingLowestPriorityContextItem()?.first ?: break
     * }
     * ```
     *
     * @return The trimmed request and the dropped item, or `null` when the context is already
     * empty.
     */
    public fun droppingLowestPriorityContextItem(): Pair<ModelRequest, EvaluationContext.Item>? {
        val (trimmed, dropped) = context.droppingLowestPriorityItem() ?: return null
        return ModelRequest(instructions, schema, trimmed, render) to dropped
    }
}

/** Selects which model backs an evaluation. Returned from an [ModelResolver]. */
public sealed class ModelSelector {

    /** Use the SDK's built-in model, if one is registered and the device is eligible. */
    public data object DefaultModel : ModelSelector()

    /**
     * Use a custom model — your own backend, a third-party API, or another on-device runtime.
     *
     * @param model The model.
     */
    public class Custom public constructor(public val model: ModelAdapter) : ModelSelector()
}

/** Routes a usage to a model backend. */
public fun interface ModelResolver {

    /**
     * Returns the selector to apply for [usage].
     *
     * @param usage The usage being evaluated. Compare against a feature's usage key with `==`.
     * @return The model selector.
     */
    public fun resolve(usage: Usage<*>): ModelSelector
}
