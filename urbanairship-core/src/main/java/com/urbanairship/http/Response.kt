/* Copyright Airship and Contributors */
package com.urbanairship.http

import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import com.urbanairship.util.UAHttpStatusUtil
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
            val location = header("Location") ?: return null
            return try {
                Uri.parse(location)
            } catch (e: Exception) {
                UALog.e("Failed to parse location header.")
                null
            }
        }

    /**
     * Returns the retry-after header as a non-negative [Duration], or null if absent or
     * unparseable. Accepts a non-negative integer or decimal number of seconds (per RFC
     * 7231 §7.1.3, with a permissive extension for fractional seconds), an RFC 7231
     * HTTP-date, or an ISO 8601 timestamp.
     */
    public fun getRetryAfterHeader(): Duration? = getRetryAfterHeader(Clock.DEFAULT_CLOCK)

    @VisibleForTesting
    public fun getRetryAfterHeader(clock: Clock): Duration? {
        val retryAfter = header("Retry-After")?.trim() ?: return null
        parseRetryAfter(retryAfter, clock)?.let { return it.coerceAtLeast(Duration.ZERO) }
        UALog.e("Invalid RetryAfter header %s", retryAfter)
        return null
    }

    private fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

private val DELAY_SECONDS = Regex("""\d+(\.\d+)?""")

internal fun parseRetryAfter(value: String, clock: Clock): Duration? {
    // RFC 7231 §7.1.3: delay-seconds = 1*DIGIT (extended to allow fractional seconds).
    if (DELAY_SECONDS.matches(value)) {
        return value.toDouble().seconds
    }
    // Number-like but not strict (negative, scientific, leading +): reject outright.
    // Otherwise, SimpleDateFormat would happily parse e.g. "-5" as year -5 BC.
    if (value.toDoubleOrNull() != null) {
        return null
    }
    val date = runCatching { DateUtils.parseIso8601(value) }.getOrNull()
        ?: runCatching { DateUtils.parseHttpDate(value) }.getOrNull()
        ?: return null
    return (date - clock.currentTimeMillis()).milliseconds
}
