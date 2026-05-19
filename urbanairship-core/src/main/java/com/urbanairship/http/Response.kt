/* Copyright Airship and Contributors */
package com.urbanairship.http

import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import com.urbanairship.util.UAHttpStatusUtil
import java.text.ParseException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Model object containing response information from a request.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class Response<T>(
    val status: Int,
    val result: T,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    public fun <R> map(mapper: (T) -> R): Response<R> {
        return Response(
            this.status,
            mapper(this.result),
            this.body,
            this.headers,
        )
    }

    /**
     * True if the status is 200-299, otherwise false.
     *
     * @return `true` if the status is 200-299, otherwise `false`.
     */
    val isSuccessful: Boolean
        get() = UAHttpStatusUtil.inSuccessRange(status)

    /**
     * True if the status is 500-599, otherwise false.
     *
     * @return `true` if the status is 500-599, otherwise `false`.
     */
    val isServerError: Boolean
        get() = UAHttpStatusUtil.inServerErrorRange(status)

    /**
     * True if the status is 400-499, otherwise false.
     *
     * @return `true` if the status is 400-499, otherwise `false`.
     */
    val isClientError: Boolean
        get() = UAHttpStatusUtil.inClientErrorRange(status)

    /**
     * True if the status is 429, otherwise false.
     *
     * @return `true` if the status is 429, otherwise `false`.
     */
    val isTooManyRequestsError: Boolean
        get() = status == 429

    /**
     * Returns the location header if set.
     *
     * @return The location header if set, otherwise null.
     */
    val locationHeader: Uri?
        get() {
            val location = headers["Location"] ?: return null
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
        val retryAfter = headers["Retry-After"] ?: return null
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
