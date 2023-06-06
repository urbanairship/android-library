/* Copyright Airship and Contributors */

package com.urbanairship.http

import android.util.Log
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.util.UAHttpStatusUtil
import kotlinx.coroutines.withContext

/**
 * A request session that will suspend and perform blocking operations on a thread pool.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SuspendingRequestSession(private val requestSession: RequestSession) {

    public suspend fun execute(request: Request): RequestResult<Unit> {
        return execute(request) { _, _, _ -> }
    }

    public suspend fun <T> execute(
        request: Request,
        parser: ResponseParser<T?>
    ): RequestResult<T> {
        return withContext(AirshipDispatchers.IO) {
            try {
                val response = requestSession.execute(request, parser)
                RequestResult(
                    status = response.status,
                    value = response.result,
                    body = response.body,
                    headers = response.headers
                )
            } catch (e: Exception) {
                RequestResult(exception = e)
            }
        }
    }
}

/**
 * Wraps the request session into a suspending request session.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RequestSession.toSuspendingRequestSession(): SuspendingRequestSession {
    return SuspendingRequestSession(this)
}

/**
 * Request result.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class RequestResult<T>(
    val status: Int? = null,
    val value: T? = null,
    val body: String?,
    val headers: Map<String, String>?,
    val exception: Throwable?,
) {

    public constructor(status: Int, value: T?, body: String?, headers: Map<String, String>?) : this(
        status = status,
        value = value,
        body = body,
        headers = headers,
        exception = null,
    )

    public constructor(exception: Throwable) : this(
        status = null,
        value = null,
        body = null,
        headers = null,
        exception = exception,
    )

    public fun <R> map(mapper: (T?) -> R?): RequestResult<R> {
        return RequestResult(
            this.status, mapper(this.value), body, headers, exception
        )
    }

    public val isSuccessful: Boolean
        get() = status != null && UAHttpStatusUtil.inSuccessRange(status)

    public val isServerError: Boolean
        get() = status != null && UAHttpStatusUtil.inServerErrorRange(status)

    public val isClientError: Boolean
        get() = status != null && UAHttpStatusUtil.inClientErrorRange(status)

    public val isTooManyRequestsError: Boolean
        get() = status != null && status == 429
}

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun RequestResult<*>.log(message: () -> String) {
    when {
        this.exception != null -> {
            when (this.exception) {
                is JsonException -> UALog.log(Log.ERROR, this.exception, message)
                is RequestException -> UALog.log(Log.DEBUG, this.exception, message)
                else -> UALog.log(Log.WARN, this.exception, message)
            }
        }
        this.isClientError -> UALog.log(Log.ERROR, null, message)
        else -> UALog.log(Log.DEBUG, null, message)
    }
}
