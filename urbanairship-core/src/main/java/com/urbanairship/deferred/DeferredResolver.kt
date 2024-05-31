package com.urbanairship.deferred

import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.RequestResult
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * @hide
 */
@OpenForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DeferredResolver internal constructor(
    private val audienceOverridesProvider: AudienceOverridesProvider,
    private val apiClient: DeferredApiClient,
    private val locationMap: ConcurrentHashMap<Uri, Uri> = ConcurrentHashMap()
) {
    internal constructor(config: AirshipRuntimeConfig, audienceOverridesProvider: AudienceOverridesProvider) : this(
        audienceOverridesProvider = audienceOverridesProvider,
        apiClient = DeferredApiClient(config, config.requestSession.toSuspendingRequestSession())
    )

    public suspend fun <T> resolve(request: DeferredRequest, parser: (JsonValue) -> T): DeferredResult<T> {
        return doResolve(
            uri = locationMap[request.uri] ?: request.uri,
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

        when (response.status) {
            200 -> {
                val value = response.value ?: return DeferredResult.RetriableError()
                if (value.isNull) {
                    return DeferredResult.RetriableError()
                }
                return try {
                    DeferredResult.Success(resultParser(value))
                } catch (ex: Exception) {
                    UALog.e(ex) { "Failed ot parse deferred" }
                    DeferredResult.RetriableError()
                }
            }
            404 -> return DeferredResult.NotFound()
            409 -> return DeferredResult.OutOfDate()
            429 -> {
                response.locationHeader?.let { locationMap.put(uri, it) }
                return DeferredResult.RetriableError<T>(response.getRetryAfterHeader(TimeUnit.MILLISECONDS, 0))
            }
            307 -> {
                val redirect = response.locationHeader
                    ?: return DeferredResult.RetriableError<T>(response.getRetryAfterHeader(TimeUnit.MILLISECONDS, 0))
                locationMap[uri] = redirect

                val retryDelay = response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1)
                if (retryDelay > 0) {
                    return DeferredResult.RetriableError<T>(retryDelay)
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

                return DeferredResult.RetriableError<T>()
            }
            else -> return DeferredResult.RetriableError<T>()
        }
    }
}
