/* Copyright Airship and Contributors */
package com.urbanairship.ai

import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Runs an evaluation against a model, applying the retry and validation policy.
 *
 * @param maxResponseTimeout A hard ceiling on an evaluation's total wall-clock time, including
 * every retry and the delays between them, independent of the schedule
 * [Model.retryDecision] picks. A backstop against a pathological hang, not a latency target.
 * @param observerScope Where observer callbacks are dispatched.
 */
internal class Evaluator(
    private val maxResponseTimeout: Duration = DEFAULT_MAX_RESPONSE_TIMEOUT,
    private val observerScope: CoroutineScope =
        CoroutineScope(AirshipDispatchers.IO + SupervisorJob())
) {

    suspend fun <Output, Subject> evaluate(
        evaluation: Evaluation<Output, Subject>,
        model: Model,
        context: EvaluationContext,
        observer: EvaluationObserver? = null
    ): EvaluationResult<Output> {
        val usage = evaluation.usage
        val schema = evaluation.schema

        val request = ModelRequest(
            instructions = evaluation.instructions(),
            schema = schema,
            context = context,
            render = evaluation::prompt
        )

        if (model.availability != Availability.Available) {
            // A model that never runs is the common outcome in the field, and the one an
            // observer most needs to see, so it is reported like any other.
            report(
                observer,
                EvaluationRecord(
                    usage = usage,
                    request = request,
                    outcome = EvaluationRecord.Outcome.Skipped(MODEL_UNAVAILABLE),
                    duration = Duration.ZERO,
                    attempts = 0
                )
            )
            return EvaluationResult.Skipped(MODEL_UNAVAILABLE)
        }

        // Metadata only: never the instructions, schema, prompt, context, or response. An
        // evaluation's inputs and outputs leave the SDK only through a registered observer.
        UALog.v { "AI evaluate [${usage.rawValue}] starting, context items: ${context.items.size}" }

        val started = TimeSource.Monotonic.markNow()
        var attempts = 0

        val json = try {
            withTimeout(maxResponseTimeout) {
                withRetry(model, usage) {
                    attempts += 1
                    val response = model.respond(request)
                    // Validated inside the retry loop so another attempt can correct bad
                    // output, and wrapped so retryDecision can tell a non-conforming answer
                    // apart from a failure thrown by respond itself.
                    try {
                        schema.validate(response)
                    } catch (e: Exception) {
                        throw SchemaValidationException(e)
                    }
                    response
                }
            }
        } catch (e: TimeoutCancellationException) {
            return fail(observer, usage, request, started.elapsedNow(), attempts, e)
        } catch (e: CancellationException) {
            // A cancelled caller must see the cancellation; a model that threw
            // CancellationException on its own is just a failure.
            currentCoroutineContext().ensureActive()
            return fail(observer, usage, request, started.elapsedNow(), attempts, e)
        } catch (e: Exception) {
            return fail(observer, usage, request, started.elapsedNow(), attempts, e)
        }

        val duration = started.elapsedNow()
        UALog.v { "AI evaluate [${usage.rawValue}] completed in $duration" }

        // Reported before parsing, so an output the feature can't parse is still visible.
        report(
            observer,
            EvaluationRecord(
                usage = usage,
                request = request,
                outcome = EvaluationRecord.Outcome.Completed(json),
                duration = duration,
                attempts = attempts
            )
        )

        return try {
            EvaluationResult.Completed(evaluation.parseOutput(json))
        } catch (e: Exception) {
            UALog.w(e) { "AI evaluation output could not be parsed for ${usage.rawValue}" }
            EvaluationResult.Failed(e)
        }
    }

    /**
     * Reports an evaluation the manager skipped before a model was consulted, so the observer
     * contract holds for outcomes that never reach [evaluate].
     */
    fun reportSkipped(
        evaluation: Evaluation<*, *>,
        context: EvaluationContext,
        reason: String,
        observer: EvaluationObserver?
    ) {
        observer ?: return
        report(
            observer,
            EvaluationRecord(
                usage = evaluation.usage,
                request = ModelRequest(
                    instructions = evaluation.instructions(),
                    schema = evaluation.schema,
                    context = context,
                    render = evaluation::prompt
                ),
                outcome = EvaluationRecord.Outcome.Skipped(reason),
                duration = Duration.ZERO,
                attempts = 0
            )
        )
    }

    private fun <Output> fail(
        observer: EvaluationObserver?,
        usage: Usage<*>,
        request: ModelRequest,
        duration: Duration,
        attempts: Int,
        error: Throwable
    ): EvaluationResult<Output> {
        UALog.w(error) { "AI evaluation failed for ${usage.rawValue}" }
        report(
            observer,
            EvaluationRecord(
                usage = usage,
                request = request,
                outcome = EvaluationRecord.Outcome.Failed(error),
                duration = duration,
                attempts = attempts
            )
        )
        return EvaluationResult.Failed(error)
    }

    /**
     * Runs [operation], asking the model after each failure whether to retry and after how
     * long. The model picks the schedule; the caller's `withTimeout` caps the total time it
     * gets to do so.
     */
    private suspend fun <T> withRetry(
        model: Model,
        usage: Usage<*>,
        operation: suspend () -> T
    ): T {
        var attempt = 0

        while (true) {
            attempt += 1
            currentCoroutineContext().ensureActive()

            val error = try {
                return operation()
            } catch (e: CancellationException) {
                // Cancellation (e.g. the timeout firing) is terminal — propagate it rather
                // than burning a retry on it.
                throw e
            } catch (e: Exception) {
                e
            }

            UALog.w(error) { "AI evaluation attempt $attempt failed for ${usage.rawValue}" }

            when (val decision = model.retryDecision(usage, error, attempt)) {
                RetryDecision.Fail -> throw error
                is RetryDecision.Retry -> if (decision.after > Duration.ZERO) {
                    // retryDecision is app-implementable, so clamp: anything past the ceiling
                    // would be cut off by the enclosing timeout anyway.
                    delay(minOf(decision.after, maxResponseTimeout))
                }
            }
        }
    }

    /**
     * Hands a finished evaluation to the observer on a coroutine of its own, so an observer
     * that blocks — or one that reaches back into the SDK — can't delay the result reaching the
     * feature that asked for it.
     */
    private fun report(observer: EvaluationObserver?, record: EvaluationRecord) {
        observer ?: return
        observerScope.launch {
            try {
                observer.onEvaluation(record)
            } catch (e: Exception) {
                // App code on an Airship pool thread — an uncaught throw here would take the
                // process down with no app frames in the trace.
                UALog.e(e) { "AI evaluation observer threw" }
            }
        }
    }

    internal companion object {
        /** Wall-clock ceiling on a whole evaluation, retries and their delays included. */
        val DEFAULT_MAX_RESPONSE_TIMEOUT: Duration = 120.seconds

        private const val MODEL_UNAVAILABLE = "Model unavailable"
    }
}
