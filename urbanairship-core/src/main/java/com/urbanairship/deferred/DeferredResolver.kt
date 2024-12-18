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
        if (isUrlOutDated(request.uri)) {
            return DeferredResult.OutOfDate()
        }

        return doResolve(
            uri = resolveUrlMapping(request.uri),
            channelId = request.channelId,
            contactId = request.contactId,
            stateOverrides = StateOverrides(request),
            audienceOverrides = audienceOverridesProvider.channelOverrides(request.channelId, request.contactId),
            triggerContext = request.triggerContext,
            resultParser = parser,
            allowRetry = true
        )
    }

    private suspend fun <T> doResolve(
        uri: Uri,
        channelId: String,
        contactId: String?,
        stateOverrides: StateOverrides,
        audienceOverrides: AudienceOverrides.Channel,
        triggerContext: DeferredTriggerContext?,
        resultParser: (JsonValue) -> T,
        allowRetry: Boolean
    ): DeferredResult<T> {

        val response: RequestResult<JsonValue>
        try {
            response = apiClient.resolve(
                uri = uri,
                channelId = channelId,
                contactId = contactId,
                stateOverrides = stateOverrides,
                audienceOverrides = audienceOverrides,
                triggerContext = triggerContext
            )
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to resolve deferred: $uri" }
            return DeferredResult.TimedOut()
        }

        when (val statusCode = response.status) {
            200 -> {
                val value = response.value
                // Check for null value or null JsonValue
                if (value == null || value.isNull) {
                    return DeferredResult.RetriableError(statusCode = statusCode)
                }
                return try {
                    DeferredResult.Success(resultParser(value))
                } catch (ex: Exception) {
                    UALog.e(ex) { "Failed to parse deferred!" }
                    DeferredResult.RetriableError(statusCode = statusCode)
                }
            }
            404 -> return DeferredResult.NotFound()
            409 -> {
                addOutdatedUrl(uri)
                return DeferredResult.OutOfDate()
            }
            429 -> {
                response.locationHeader?.let {
                    addUrlMapping(uri, it)
                }
                return DeferredResult.RetriableError(
                    retryAfter = response.getRetryAfterHeader(TimeUnit.MILLISECONDS, 0),
                    statusCode = statusCode
                )
            }
            307 -> {
                val redirect = response.locationHeader
                    ?: return DeferredResult.RetriableError(
                        retryAfter = response.getRetryAfterHeader(TimeUnit.MILLISECONDS, 0),
                        statusCode = statusCode
                    )
                addUrlMapping(uri, redirect)

                val retryDelay = response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1)
                if (retryDelay > 0) {
                    return DeferredResult.RetriableError(retryDelay, statusCode = statusCode)
                }

                if (allowRetry) {
                    return doResolve(
                        uri = redirect,
                        channelId = channelId,
                        contactId = contactId,
                        stateOverrides = stateOverrides,
                        audienceOverrides = audienceOverrides,
                        triggerContext = triggerContext,
                        resultParser = resultParser,
                        allowRetry = false
                    )
                }

                return DeferredResult.RetriableError(statusCode = statusCode)
            }
            else -> return DeferredResult.RetriableError(statusCode = statusCode)
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
