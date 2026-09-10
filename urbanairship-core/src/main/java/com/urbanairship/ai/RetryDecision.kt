/* Copyright Airship and Contributors */
package com.urbanairship.ai

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What the evaluator does after a failed attempt. */
public sealed class RetryDecision {

    /**
     * Try again after a delay.
     *
     * @param after How long to wait; [Duration.ZERO] retries immediately. The framework's
     * wall-clock ceiling still applies, so a delay past it ends the evaluation instead.
     */
    public data class Retry public constructor(public val after: Duration) : RetryDecision()

    /** Stop; the evaluation fails with the error that triggered this decision. */
    public data object Fail : RetryDecision()

    public companion object {

        /** Attempts [defaultBackoff] allows before giving up. */
        public const val DEFAULT_MAX_ATTEMPTS: Int = 3

        /**
         * The framework's default retry policy, used by [ModelInterface.retryDecision]'s default and
         * available to models that want to fall back to it selectively.
         *
         * An [SchemaValidationException] retries immediately — the model already answered, it
         * just didn't conform. Any other error backs off 1s, then 4s. Fails after
         * [DEFAULT_MAX_ATTEMPTS] attempts either way.
         *
         * @param error The error from the attempt that just failed.
         * @param attempt The attempt number that just failed, starting at 1.
         * @return The decision.
         */
        @JvmStatic
        public fun defaultBackoff(error: Throwable, attempt: Int): RetryDecision = when {
            attempt >= DEFAULT_MAX_ATTEMPTS -> Fail
            error is SchemaValidationException -> Retry(Duration.ZERO)
            attempt == 1 -> Retry(1.seconds)
            else -> Retry(4.seconds)
        }
    }
}

/**
 * Thrown when a model's response doesn't conform to the evaluation's schema.
 *
 * The model already answered — it just produced a shape that doesn't match — so this is a
 * different failure than an error thrown by [ModelInterface.respond] itself. An
 * [ModelInterface.retryDecision] implementation can test for this type to retry it on a different
 * schedule than a network or timeout error. The constructor is public so that branch can be
 * unit-tested.
 *
 * @param cause The error describing what didn't conform.
 */
public class SchemaValidationException public constructor(
    override val cause: Throwable
) : Exception("AI model response did not conform to the evaluation schema", cause)
