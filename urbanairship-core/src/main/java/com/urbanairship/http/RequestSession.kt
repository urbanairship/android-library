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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
     * Returns the retry-after header as a [Duration], or null if absent or unparseable.
     * Accepts an integer or decimal number of seconds (per RFC 7231) or an ISO 8601 HTTP-date.
     */
    public fun getRetryAfterHeader(): Duration? = getRetryAfterHeader(Clock.DEFAULT_CLOCK)

    @VisibleForTesting
    public fun getRetryAfterHeader(clock: Clock): Duration? {
        val retryAfter = headers?.get("Retry-After") ?: return null
        retryAfter.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { return it.seconds }
        try {
            val retryDate = DateUtils.parseIso8601(retryAfter)
            return (retryDate - clock.currentTimeMillis()).milliseconds
        } catch (ignored: ParseException) {
        }
        UALog.e("Invalid RetryAfter header %s", retryAfter)
        return null
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
