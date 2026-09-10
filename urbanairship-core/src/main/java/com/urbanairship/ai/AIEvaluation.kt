/* Copyright Airship and Contributors */
package com.urbanairship.ai

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSchema
import com.urbanairship.json.JsonValue

/**
 * A typed request a feature module submits to the resolved model.
 *
 * [Output] is parsed from the model's JSON response; [Subject] is passed to the registered
 * [AIContextProvider] at evaluation time.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AIEvaluation<Output, Subject> {

    /** Which AI usage this belongs to, binding the subject type the provider receives. */
    public val usage: AIUsage<Subject>

    /** The subject passed to the registered [AIContextProvider]. */
    public val subject: Subject

    /** The output contract. Payload-driven features parse this from the payload. */
    public val schema: JsonSchema

    /**
     * When `true`, skips the evaluation if the resolved context is empty.
     *
     * Opt in only when an empty context makes the model's answer arbitrary; most evaluations
     * run regardless and fail open.
     */
    public val requiresContext: Boolean
        get() = false

    /**
     * Returns the system instructions: the model's role, rules, and output expectations.
     *
     * @return The instructions.
     */
    public fun instructions(): String

    /**
     * Builds the prompt, rendering [context] into it.
     *
     * Nothing enforces that [context] is used, but ignoring it wastes the fetch and the trim.
     * An evaluation that wants no context should have no provider registered instead.
     *
     * @param context The context as trimmed so far; called again after each drop when the
     * model's input window is tight.
     * @return The prompt.
     */
    public fun prompt(context: AIContext): String

    /**
     * Parses a schema-validated response into this evaluation's output type.
     *
     * @param json The model's response.
     * @return The parsed output.
     * @throws JsonException if the response can't be parsed.
     */
    @Throws(JsonException::class)
    public fun parseOutput(json: JsonValue): Output
}

/**
 * The outcome of a model evaluation.
 *
 * Treat anything but [Completed] as "no opinion" and proceed with default behavior.
 */
public sealed class AIEvaluationResult<out Output> {

    /**
     * The model produced a structured result.
     *
     * @param value The structured output.
     */
    public data class Completed<out Output> public constructor(
        public val value: Output
    ) : AIEvaluationResult<Output>()

    /**
     * The evaluation never ran — model unavailable, no context, AI disabled.
     *
     * @param reason Why it didn't run.
     */
    public data class Skipped public constructor(
        public val reason: String
    ) : AIEvaluationResult<Nothing>()

    /**
     * The evaluation ran but threw.
     *
     * @param error The error.
     */
    public data class Failed public constructor(
        public val error: Throwable
    ) : AIEvaluationResult<Nothing>()

    /** The structured output if the evaluation completed, otherwise `null`. */
    public val output: Output?
        get() = when (this) {
            is Completed -> value
            else -> null
        }
}
