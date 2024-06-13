/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.cache.AirshipCache
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.deferred.DeferredResult
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.util.Clock
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class FlagDeferredResolver(
    private val cache: AirshipCache,
    private val resolver: DeferredResolver,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) {
    private val pendingTasks: MutableMap<String, Deferred<Result<DeferredFlag>>> = mutableMapOf()
    private val backOffIntervals: MutableMap<String, Long> = mutableMapOf()

    suspend fun resolve(request: DeferredRequest, flagInfo: FeatureFlagInfo): Result<DeferredFlag> {
        val requestId = listOf(flagInfo.name, flagInfo.id, flagInfo.lastUpdated, request.contactId ?: "", request.uri.toString())
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

    private suspend fun resolve(request: DeferredRequest, flagInfo: FeatureFlagInfo, requestId: String): Result<DeferredFlag> {
        val cached = try {
            cache.getCached(requestId, DeferredFlag::fromJson)
        } catch (e: JsonException) {
            UALog.w(e) { "Failed to parse cached deferred flag!" }
            null
        }

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

    private suspend fun fetchFlag(request: DeferredRequest, requestId: String, info: FeatureFlagInfo, allowRetry: Boolean): Result<DeferredFlag> {
        backOffIntervals[requestId]?.let {
            val delayMs = it - clock.currentTimeMillis()
            if (delayMs > 0) {
                delay(delayMs)
            }
            backOffIntervals.remove(requestId)
        }

        val result = try {
            resolver.resolve(request, DeferredFlagInfo::fromJson)
        } catch (e: JsonException) {
            UALog.w(e) { "Failed to parse resolved deferred flag info!" }
            null
        }

        when (result) {
            is DeferredResult.Success<DeferredFlagInfo> -> {
                return Result.success(DeferredFlag.Found(result.result))
            }
            is DeferredResult.NotFound -> {
                return Result.success(DeferredFlag.NotFound)
            }
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

internal sealed class DeferredFlag: JsonSerializable {

    internal enum class ResultType(val jsonValue: String) {
        FOUND("found"),
        NOT_FOUND("not_found");

        companion object {
            @Throws(JsonException::class)
            fun fromJson(jsonValue: JsonValue): ResultType {
                val string = jsonValue.requireString()
                return entries.firstOrNull { it.jsonValue == string } ?: throw JsonException("Invalid result type: $jsonValue")
            }
        }
    }
    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_FLAG = "flag"

        @Throws(JsonException::class)
        fun fromJson(jsonValue: JsonValue): DeferredFlag {
            val map = jsonValue.requireMap()
            return when (ResultType.fromJson(map.requireField(KEY_TYPE))) {
                ResultType.NOT_FOUND -> NotFound
                ResultType.FOUND -> Found(DeferredFlagInfo.fromJson(map.requireField(KEY_FLAG)))
            }
        }
    }

    data class Found(val flagInfo: DeferredFlagInfo): DeferredFlag() {

        @Throws(JsonException::class)
        override fun toJsonValue(): JsonValue {
            return jsonMapOf(KEY_TYPE to ResultType.FOUND.jsonValue, KEY_FLAG to flagInfo).toJsonValue()
        }
    }

    data object NotFound: DeferredFlag() {
        @Throws(JsonException::class)
        override fun toJsonValue(): JsonValue {
            return jsonMapOf(KEY_TYPE to ResultType.NOT_FOUND.jsonValue).toJsonValue()
        }
    }
}

internal data class DeferredFlagInfo(
    val isEligible: Boolean,
    val variables: FeatureFlagVariables?,
    val reportingMetadata: JsonMap
): JsonSerializable {
    companion object {
        private const val KEY_IS_ELIGIBLE = "is_eligible"
        private const val KEY_VARIABLES = "variables"
        private const val KEY_REPORTING_METADATA = "reporting_metadata"

        @Throws(JsonException::class)
        fun fromJson(jsonValue: JsonValue): DeferredFlagInfo {
            val map = jsonValue.optMap()
            return DeferredFlagInfo(
                isEligible = map.requireField(KEY_IS_ELIGIBLE),
                variables = map.optionalField<JsonMap?>(KEY_VARIABLES)?.let { FeatureFlagVariables.fromJson(it) },
                reportingMetadata = map.requireField(KEY_REPORTING_METADATA)
            )
        }
    }

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            KEY_IS_ELIGIBLE to isEligible,
            KEY_VARIABLES to variables,
            KEY_REPORTING_METADATA to reportingMetadata
        ).toJsonValue()
    }
}
