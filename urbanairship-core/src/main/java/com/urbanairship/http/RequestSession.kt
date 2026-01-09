package com.urbanairship.http

import android.net.Uri
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import com.urbanairship.util.UAHttpStatusUtil
import java.text.ParseException
import java.util.concurrent.TimeUnit

/**
 * Parses a response.
 *
 * @param <T> The result type.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface RequestSession {

    public var channelAuthTokenProvider: AuthTokenProvider?
    public var contactAuthTokenProvider: AuthTokenProvider?

    public suspend fun execute(request: Request): RequestResult<Unit>

    /**
     * Executes the request.
     *
     * @return The request response.
     */
    public suspend fun <T> execute(request: Request, parser: ResponseParser<T?>): RequestResult<T>
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
    internal val shouldRetry: Boolean = false
) {

    internal constructor(response: Response<T?>, shouldRetry: Boolean) : this(
        status = response.status,
        value = response.result,
        body = response.body,
        headers = response.headers,
        exception = null,
        shouldRetry = shouldRetry
    )

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
        get() = status == 429

    /**
     * Returns the location header if set.
     *
     * @return The location header if set, otherwise null.
     */
    public val locationHeader: Uri?
        get() {
            val location = headers?.get("Location") ?: return null
            return try {
                Uri.parse(location)
            } catch (e: Exception) {
                UALog.e("Failed to parse location header.")
                null
            }
        }

    /**
     * Returns the retry-after header if set.
     *
     * @param timeUnit The resulting time unit.
     * @param defaultValue The default value.
     * @return The retry-after in the time unit if set, otherwise the defaultValue.
     */
    public fun getRetryAfterHeader(timeUnit: TimeUnit, defaultValue: Long): Long {
        return getRetryAfterHeader(timeUnit, defaultValue, Clock.DEFAULT_CLOCK)
    }

    @VisibleForTesting
    public fun getRetryAfterHeader(timeUnit: TimeUnit, defaultValue: Long, clock: Clock): Long {
        val retryAfter = headers?.get("Retry-After") ?: return defaultValue
        try {
            val retryDate = DateUtils.parseIso8601(retryAfter)
            val milliseconds = retryDate - clock.currentTimeMillis()
            return timeUnit.convert(milliseconds, TimeUnit.MILLISECONDS)
        } catch (ignored: ParseException) {
        }
        try {
            val seconds = retryAfter.toLong()
            return timeUnit.convert(seconds, TimeUnit.SECONDS)
        } catch (ignored: Exception) {
        }
        UALog.e("Invalid RetryAfter header %s", retryAfter)
        return defaultValue
    }
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
