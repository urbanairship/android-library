/* Copyright Airship and Contributors */
package com.urbanairship.ai

import com.urbanairship.json.JsonSchema
import com.urbanairship.json.JsonValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The framework's interface to a model backend.
 *
 * Answers one request; retry, timeout, and output validation live in the framework, tuned
 * through [maxAttempts] and [responseTimeout].
 */
public interface AIModel {

    /**
     * Whether the model can be used right now, and if not, why.
     *
     * Read fresh before each evaluation, which is skipped when this is
     * [AIModelAvailability.Unavailable]. Override only for a backend that can genuinely be
     * unusable; otherwise let a failure surface from [respond].
     */
    public val availability: AIModelAvailability
        get() = AIModelAvailability.Available

    /**
     * Emits whenever [availability] changes.
     *
     * The default emits the current value once and completes, which is right for a model whose
     * availability never changes. Treat emissions as change notifications and read
     * [availability] for the value right now.
     */
    public val availabilityUpdates: Flow<AIModelAvailability>
        get() = flowOf(availability)

    /** Attempts, including the first, before the framework fails the evaluation. */
    public val maxAttempts: Int
        get() = DEFAULT_MAX_ATTEMPTS

    /**
     * Wall-clock budget across *all* attempts, not per attempt.
     *
     * A backend answering over the network should raise it, lower [maxAttempts], or both.
     */
    public val responseTimeout: Duration
        get() = DEFAULT_RESPONSE_TIMEOUT

    /**
     * Answers a single request.
     *
     * At minimum, send [AIModelRequest.prompt]. If the prompt exceeds your input limit, shrink
     * it with [AIModelRequest.droppingLowestPriorityContextItem].
     *
     * @param request The request to answer.
     * @return The model's structured response.
     */
    public suspend fun respond(request: AIModelRequest): JsonValue

    public companion object {
        /** Attempts a model makes by default before the framework fails the evaluation. */
        public const val DEFAULT_MAX_ATTEMPTS: Int = 3

        /** Wall-clock budget a model allows by default across all attempts. */
        public val DEFAULT_RESPONSE_TIMEOUT: Duration = 30.seconds
    }
}

/** Whether a resolved model can be used right now. */
public sealed class AIModelAvailability {

    /** The model can be used. */
    public data object Available : AIModelAvailability()

    /**
     * The model can't be used.
     *
     * @param reason Why not.
     */
    public data class Unavailable public constructor(
        public val reason: Reason
    ) : AIModelAvailability()

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
 * A single request handed to an [AIModel]: instructions, output schema, and prioritized context.
 *
 * The originating evaluation owns how context is labeled and laid out — a model only renders it
 * via [prompt] and, if its input window is tight, trims. The framework builds one per
 * evaluation; models don't construct these.
 */
public class AIModelRequest internal constructor(

    /** The system instructions: the model's role and rules. */
    public val instructions: String,

    /** The contract the response must conform to. */
    public val schema: JsonSchema,

    /** The prioritized context for this request. */
    public val context: AIContext,

    private val render: (AIContext) -> String
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
    public fun droppingLowestPriorityContextItem(): Pair<AIModelRequest, AIContext.Item>? {
        val (trimmed, dropped) = context.droppingLowestPriorityItem() ?: return null
        return AIModelRequest(instructions, schema, trimmed, render) to dropped
    }
}

/** Selects which model backs an evaluation. Returned from an [AIModelResolver]. */
public sealed class AIModelSelector {

    /** Use the SDK's built-in model, if one is registered and the device is eligible. */
    public data object DefaultModel : AIModelSelector()

    /**
     * Use a custom model — your own backend, a third-party API, or another on-device runtime.
     *
     * @param model The model.
     */
    public class Custom public constructor(public val model: AIModel) : AIModelSelector()
}

/** Routes a usage to a model backend. */
public fun interface AIModelResolver {

    /**
     * Returns the selector to apply for [usage].
     *
     * @param usage The usage being evaluated. Compare against a feature's usage key with `==`.
     * @return The model selector.
     */
    public fun resolve(usage: AIUsage<*>): AIModelSelector
}
