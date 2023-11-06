package com.urbanairship.featureflag

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.cache.AirshipCache
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.deferred.DeferredResult
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.util.Clock
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class FlagDeferredResolver(
    private val cache: AirshipCache,
    private val resolver: DeferredResolver,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) {
    private val pendingTasks: MutableMap<String, Deferred<Result<FeatureFlag>>> = mutableMapOf()
    private val backOffIntervals: MutableMap<String, Long> = mutableMapOf()

    suspend fun resolve(request: DeferredRequest, flagInfo: FeatureFlagInfo): Result<FeatureFlag> {
        val requestId = listOf(flagInfo.name, flagInfo.id, flagInfo.lastUpdated, request.contactID ?: "", request.uri.toString())
            .joinToString(":")

        return withContext(dispatcher) {
            // Wait for any pending requests
            pendingTasks[requestId]?.await()

            // Execute new request
            val execution = async(dispatcher) {
                resolve(request, flagInfo, requestId)
            }

            // Store it as deferred
            pendingTasks[requestId] = execution
            execution.await()
        }
    }

    private suspend fun resolve(request: DeferredRequest, flagInfo: FeatureFlagInfo, requestId: String): Result<FeatureFlag> {
        val cached = cache.getCached(requestId, FeatureFlag::fromJson)
        if (cached != null) {
            return Result.success(cached)
        }

        val result = fetchFlag(
            request = request,
            requestId = requestId,
            info = flagInfo,
            allowRetry = true
        )

        val flag = result.getOrNull()
        if (result.isSuccess && flag != null) {
            val ttl = flagInfo.evaluationOptions?.ttl?.let { max(MIN_CACHE_TIME_MS, it) }
                ?: MIN_CACHE_TIME_MS
            cache.store(flag, requestId, ttl)
        }

        return result
    }

    private suspend fun fetchFlag(request: DeferredRequest, requestId: String, info: FeatureFlagInfo, allowRetry: Boolean): Result<FeatureFlag> {
        backOffIntervals[requestId]?.let {
            val delayMs = it - clock.currentTimeMillis()
            if (delayMs > 0) {
                delay(delayMs)
            }
            backOffIntervals.remove(requestId)
        }

        when (val result = resolver.resolve(request, DeferredFlagResult::fromJson)) {
            is DeferredResult.Success<DeferredFlagResult> -> return Result.success(
                FeatureFlag.createFlag(
                    name = info.name,
                    isEligible = result.result.isEligible,
                    reportingInfo = FeatureFlag.ReportingInfo(
                        reportingMetadata = result.result.reportingMetadata,
                        channelId = request.channelID,
                        contactId = request.contactID
                    ),
                    variables = result.result.variables
                )
            )
            is DeferredResult.NotFound -> return Result.success(
                FeatureFlag.createMissingFlag(info.name)
            )
            is DeferredResult.RetriableError -> {
                val backOff = result.retryAfter ?: DEFAULT_BACKOFF_MS
                if (!allowRetry || backOff > IMMEDIATE_BACKOFF_RETRY_MS) {
                    backOffIntervals[requestId] = clock.currentTimeMillis() + backOff
                    return Result.failure(FeatureFlagEvaluationException.ConnectionError())
                }

                if (backOff > 0) {
                    delay(backOff)
                }

                return fetchFlag(
                    request = request,
                    requestId = requestId,
                    info = info,
                    allowRetry = false
                )
            }
            is DeferredResult.OutOfDate -> return Result.failure(FeatureFlagEvaluationException.OutOfDate())
            else -> return Result.failure(FeatureFlagEvaluationException.ConnectionError())
        }
    }

    private companion object {
        const val MIN_CACHE_TIME_MS: ULong = 60000u
        const val DEFAULT_BACKOFF_MS: Long = 30000
        const val IMMEDIATE_BACKOFF_RETRY_MS = 5000
    }
}

private data class DeferredFlagResult(
    val isEligible: Boolean,
    val variables: JsonMap?,
    val reportingMetadata: JsonMap
) {
    companion object {
        const val KEY_IS_ELIGIBLE = "is_eligible"
        const val KEY_VARIABLES = "variables"
        const val KEY_REPORTING_METADATA = "reporting_metadata"

        fun fromJson(jsonValue: JsonValue): DeferredFlagResult {
            val map = jsonValue.optMap()
            return DeferredFlagResult(
                isEligible = map.requireField(KEY_IS_ELIGIBLE),
                variables = map.optionalField(KEY_VARIABLES),
                reportingMetadata = map.requireField(KEY_REPORTING_METADATA)
            )
        }
    }
}
