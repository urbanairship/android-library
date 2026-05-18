/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo
import java.text.ParseException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date utilities.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object DateUtils {

    private val UTC = TimeZone.getTimeZone("UTC")

    private val FORMAT_NO_MILLIS = format("yyyy-MM-dd'T'HH:mm:ss'Z'")
    private val FORMAT_WITH_MILLIS = format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    /**
     * ISO 8601 parse formats, ordered most-specific first. The first format that consumes
     * the entire input wins. `XXX`/`XX` accept `Z`, `±HH:MM`, or `±HHMM`.
     */
    private val PARSE_FORMATS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ssXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss.SS",
        "yyyy-MM-dd'T'HH:mm:ss.S",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH",
        "yyyy-MM-dd HH",
        "yyyy-MM-dd",
        "yyyy-MM",
        "yyyy",
    ).map(::format)

    private val lock = Any()

    /**
     * Parses an ISO 8601 timestamp into epoch milliseconds.
     *
     * Accepted forms (UTC unless a zone is specified):
     *  - `2024`, `2024-01`, `2024-01-15`
     *  - `2024-01-15T11`, `2024-01-15T11:30`, `2024-01-15T11:30:45` (or with a space separator)
     *  - `2024-01-15T11:30:45.123` (fractional seconds; 1-3 digits)
     *  - `2024-01-15T11:30:45Z`
     *  - `2024-01-15T11:30:45+00:00` / `+0000` / `-05:00`
     *  - Any combination of the above.
     *
     * @throws ParseException if the timestamp cannot be parsed.
     */
    @Throws(ParseException::class)
    public fun parseIso8601(timeStamp: String?): Long {
        if (timeStamp.isNullOrEmpty()) {
            throw ParseException("Unable to parse null or empty timestamp", -1)
        }
        synchronized(lock) {
            for (format in PARSE_FORMATS) {
                val position = ParsePosition(0)
                val parsed = format.parse(timeStamp, position)
                if (parsed != null && position.index == timeStamp.length) {
                    return parsed.time
                }
            }
        }
        throw ParseException("Unable to parse $timeStamp", -1)
    }

    /**
     * Parses an ISO 8601 timestamp, returning [defaultValue] if parsing fails.
     */
    public fun parseIso8601(timeStamp: String?, defaultValue: Long): Long {
        return try {
            parseIso8601(timeStamp)
        } catch (_: ParseException) {
            defaultValue
        }
    }

    /**
     * Creates an ISO 8601 timestamp in UTC, e.g. `2024-01-15T12:00:00Z`.
     *
     * @param milliseconds Epoch milliseconds.
     * @param includeMillis If true, appends fractional seconds, e.g. `2024-01-15T12:00:00.123Z`.
     */
    @JvmOverloads
    @JvmStatic
    public fun createIso8601TimeStamp(milliseconds: Long, includeMillis: Boolean = false): String {
        val format = if (includeMillis) FORMAT_WITH_MILLIS else FORMAT_NO_MILLIS
        return synchronized(lock) { format.format(Date(milliseconds)) }
    }

    private fun format(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = UTC }
}
