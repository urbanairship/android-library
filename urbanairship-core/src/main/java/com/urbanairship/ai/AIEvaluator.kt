/* Copyright Airship and Contributors */
package com.urbanairship.ai

import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Runs an evaluation against a model, applying the retry, timeout, and validation policy. */
internal class AIEvaluator(
    private val observerScope: CoroutineScope =
        CoroutineScope(AirshipDispatchers.IO + SupervisorJob())
) {

    suspend fun <Output, Subject> evaluate(
        evaluation: AIEvaluation<Output, Subject>,
        model: AIModel,
        context: AIContext,
        observer: AIEvaluationObserver? = null
    ): AIEvaluationResult<Output> {
        val usage = evaluation.usage
        val schema = evaluation.schema

        val request = AIModelRequest(
            instructions = evaluation.instructions(),
            schema = schema,
            context = context,
            render = evaluation::prompt
        )

        if (model.availability != AIModelAvailability.Available) {
            // A model that never runs is the common outcome in the field, and the one an
            // observer most needs to see, so it is reported like any other.
            report(
                observer,
                AIEvaluationRecord(
                    usage = usage,
                    request = request,
                    outcome = AIEvaluationRecord.Outcome.Skipped(MODEL_UNAVAILABLE),
                    duration = Duration.ZERO,
                    attempts = 0
                )
            )
            return AIEvaluationResult.Skipped(MODEL_UNAVAILABLE)
        }

        // Metadata only: never the instructions, schema, prompt, context, or response. An
        // evaluation's inputs and outputs leave the SDK only through a registered observer.
        UALog.v { "AI evaluate [${usage.rawValue}] starting, context items: ${context.items.size}" }

        val started = TimeSource.Monotonic.markNow()
        var attempts = 0

        val json = try {
            withTimeout(model.responseTimeout) {
                withRetry(model.maxAttempts, usage.rawValue) {
                    attempts += 1
                    model.respond(request).also {
                        // Inside the retry loop, so another attempt can correct bad output.
                        schema.validate(it)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            return fail(observer, usage, request, started.elapsedNow(), attempts, e)
        } catch (e: CancellationException) {
            // A cancelled caller must see the cancellation; a model that threw
            // CancellationException on its own is just a failure.
            currentCoroutineContext().ensureActive()
            return fail(observer, usage, request, started.elapsedNow(), attempts, e)
        } catch (e: Throwable) {
            return fail(observer, usage, request, started.elapsedNow(), attempts, e)
        }

        val duration = started.elapsedNow()
        UALog.v { "AI evaluate [${usage.rawValue}] completed in $duration" }

        // Reported before parsing, so an output the feature can't parse is still visible.
        report(
            observer,
            AIEvaluationRecord(
                usage = usage,
                request = request,
                outcome = AIEvaluationRecord.Outcome.Completed(json),
                duration = duration,
                attempts = attempts
            )
        )

        return try {
            AIEvaluationResult.Completed(evaluation.parseOutput(json))
        } catch (e: Throwable) {
            UALog.w(e) { "AI evaluation output could not be parsed for ${usage.rawValue}" }
            AIEvaluationResult.Failed(e)
        }
    }

    private fun <Output> fail(
        observer: AIEvaluationObserver?,
        usage: AIUsage<*>,
        request: AIModelRequest,
        duration: Duration,
        attempts: Int,
        error: Throwable
    ): AIEvaluationResult<Output> {
        UALog.w(error) { "AI evaluation failed for ${usage.rawValue}" }
        report(
            observer,
            AIEvaluationRecord(
                usage = usage,
                request = request,
                outcome = AIEvaluationRecord.Outcome.Failed(error),
                duration = duration,
                attempts = attempts
            )
        )
        return AIEvaluationResult.Failed(error)
    }

    /**
     * Hands a finished evaluation to the observer on a coroutine of its own, so an observer that
     * blocks — or one that reaches back into the SDK — can't delay the result reaching the
     * feature that asked for it.
     */
    private fun report(observer: AIEvaluationObserver?, record: AIEvaluationRecord) {
        observer ?: return
        observerScope.launch { observer.onEvaluation(record) }
    }

    private suspend fun <T> withRetry(
        maxAttempts: Int,
        usage: String,
        operation: suspend () -> T
    ): T {
        val attempts = maxOf(1, maxAttempts)
        var lastError: Throwable? = null

        for (attempt in 1..attempts) {
            currentCoroutineContext().ensureActive()
            try {
                return operation()
            } catch (e: CancellationException) {
                // Cancellation (e.g. the timeout firing) is terminal — propagate it rather
                // than burning a retry on it.
                throw e
            } catch (e: Throwable) {
                UALog.w(e) { "AI evaluation attempt $attempt/$attempts failed for $usage" }
                lastError = e
            }
        }

        throw requireNotNull(lastError)
    }

    private companion object {
        const val MODEL_UNAVAILABLE = "Model unavailable"
    }
}
