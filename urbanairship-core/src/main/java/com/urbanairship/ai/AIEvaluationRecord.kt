/* Copyright Airship and Contributors */
package com.urbanairship.ai

import com.urbanairship.json.JsonValue
import kotlin.time.Duration

/**
 * A finished evaluation, handed to the observer set with [AirshipAI.setEvaluationObserver].
 *
 * Carries the context and prompt your own provider produced, so forwarding a record to a
 * third-party service sends that context off the device.
 *
 * @param usage Which feature ran.
 * @param request What was asked. The context here is what was *offered* — a model that trims to
 * fit its input window does so on its own copy.
 * @param outcome How it ended.
 * @param duration Wall-clock time across every attempt.
 * @param attempts Model calls made. Greater than 1 means output failed schema validation, or
 * the model threw, and it was retried.
 */
public class AIEvaluationRecord public constructor(
    public val usage: AIUsage<*>,
    public val request: AIModelRequest,
    public val outcome: Outcome,
    public val duration: Duration,
    public val attempts: Int
) {

    /** How an evaluation ended, before parsing into the feature's own type. */
    public sealed class Outcome {

        /**
         * The model produced output that passed schema validation.
         *
         * @param output The raw model output — untyped, so one observer can serve every usage.
         */
        public class Completed public constructor(public val output: JsonValue) : Outcome()

        /**
         * The evaluation never ran.
         *
         * @param reason Why it didn't run.
         */
        public class Skipped public constructor(public val reason: String) : Outcome()

        /**
         * The evaluation ran but threw.
         *
         * @param error The error.
         */
        public class Failed public constructor(public val error: Throwable) : Outcome()
    }
}

/** Observes finished evaluations. */
public fun interface AIEvaluationObserver {

    /**
     * Called when an evaluation finishes.
     *
     * Delivered on a coroutine of its own, off the main thread, so it never delays the result
     * reaching the feature. Records from concurrent evaluations may arrive in any order.
     *
     * @param record The finished evaluation.
     */
    public fun onEvaluation(record: AIEvaluationRecord)
}
