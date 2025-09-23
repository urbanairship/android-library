package com.urbanairship.deferred

import android.net.Uri
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.RequestResult
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonValue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * @hide
 */
@OpenForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DeferredResolver internal constructor(
    private val audienceOverridesProvider: AudienceOverridesProvider,
    private val apiClient: DeferredApiClient,
) {

    private val lock = ReentrantLock()
    private val locationMap: MutableMap<Uri, Uri> = mutableMapOf()
    private val outdatedUrls: MutableSet<Uri> = mutableSetOf()

    internal constructor(config: AirshipRuntimeConfig, audienceOverridesProvider: AudienceOverridesProvider) : this(
        audienceOverridesProvider = audienceOverridesProvider,
        apiClient = DeferredApiClient(config, config.requestSession.toSuspendingRequestSession())
    )

    public suspend fun <T> resolve(request: DeferredRequest, parser: (JsonValue) -> T): DeferredResult<T> {
        return doResolve(
            request = request,
            stateOverrides = StateOverrides(request),
            audienceOverrides = audienceOverridesProvider.channelOverrides(request.channelId, request.contactId),
            resultParser = parser,
            allowRetry = true
        )
    }

    private suspend fun <T> doResolve(
        request: DeferredRequest,
        stateOverrides: StateOverrides,
        audienceOverrides: AudienceOverrides.Channel,
        resultParser: (JsonValue) -> T,
        allowRetry: Boolean
    ): DeferredResult<T> {
        val resolvedUrl = resolveUrlMapping(request.uri)
        if (isUrlOutDated(resolvedUrl)) {
            UALog.e { "Failed to resolve deferred: $resolvedUrl. Out of date." }
            return DeferredResult.OutOfDate()
        }

        val response: RequestResult<JsonValue>
        try {
            response = apiClient.resolve(
                uri = resolvedUrl,
                channelId = request.channelId,
                contactId = request.contactId,
                stateOverrides = stateOverrides,
                audienceOverrides = audienceOverrides,
                triggerContext = request.triggerContext
            )
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to resolve deferred: $resolvedUrl" }
            return DeferredResult.TimedOut()
        }

        when (val statusCode = response.status) {
            200 -> {
                val value = response.value
                // Check for null value or null JsonValue
                if (value == null || value.isNull) {
                    UALog.w { "Failed to resolve deferred: $resolvedUrl. Missing result. Will retry." }
                    return DeferredResult.RetriableError(statusCode = statusCode)
                }
                return try {
                    DeferredResult.Success(resultParser(value))
                } catch (ex: Exception) {
                    UALog.e(ex) { "Failed to parse deferred!" }
                    DeferredResult.RetriableError(statusCode = statusCode)
                }
            }
            404 -> {
                UALog.d { "Failed to resolve deferred: $resolvedUrl. Not found." }
                return DeferredResult.NotFound(statusCode)
            }
            409 -> {
                addOutdatedUrl(resolvedUrl)
                UALog.d { "Failed to resolve deferred: $resolvedUrl. Out of date." }
                return DeferredResult.OutOfDate(statusCode)
            }
            429 -> {
                response.locationHeader?.let {
                    addUrlMapping(request.uri, it)
                }
                val retryDelay = response.getRetryAfterHeader(TimeUnit.MILLISECONDS, 0)
                UALog.d { "Failed to resolve deferred: $resolvedUrl with status code: $statusCode. Retry after $retryDelay." }
                return DeferredResult.RetriableError(
                    retryAfter = retryDelay,
                    statusCode = statusCode
                )
            }
            307 -> {
                val redirect = response.locationHeader
                if (redirect == null) {
                    val retryDelay = response.getRetryAfterHeader(TimeUnit.MILLISECONDS, 0)
                    UALog.d { "Failed to resolve deferred: $resolvedUrl with status code: $statusCode. Retry after $retryDelay." }
                    return DeferredResult.RetriableError(
                        retryAfter = retryDelay,
                        statusCode = statusCode
                    )
                }
                addUrlMapping(request.uri, redirect)

                val retryDelay = response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1)
                if (retryDelay > 0) {
                    UALog.d { "Failed to resolve deferred: $resolvedUrl with status code: $statusCode. Retry after $retryDelay." }
                    return DeferredResult.RetriableError(retryDelay, statusCode = statusCode)
                }

                if (allowRetry) {
                    return doResolve(
                        request = request,
                        stateOverrides = stateOverrides,
                        audienceOverrides = audienceOverrides,
                        resultParser = resultParser,
                        allowRetry = false
                    )
                }

                UALog.d { "Failed to resolve deferred: $resolvedUrl with status code: $statusCode. Will retry." }
                return DeferredResult.RetriableError(statusCode = statusCode)
            }
            else -> {
                UALog.d { "Failed to resolve deferred: $resolvedUrl with status code: $statusCode. Will retry." }
                return DeferredResult.RetriableError(statusCode = statusCode)
            }
        }
    }

    private fun isUrlOutDated(url: Uri): Boolean {
        return lock.withLock {
            outdatedUrls.contains(url)
        }
    }

    private fun addOutdatedUrl(url: Uri) {
        lock.withLock {
            outdatedUrls.add(url)
        }
    }

    private fun addUrlMapping(from: Uri, to: Uri) {
        lock.withLock {
            locationMap[from] = to
        }
    }

    private fun resolveUrlMapping(url: Uri): Uri {
        return lock.withLock {
            locationMap[url] ?: url
        }
    }
}
