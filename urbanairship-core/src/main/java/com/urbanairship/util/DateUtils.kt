/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
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

    /**
     * Matches the fractional-seconds component of an ISO 8601 timestamp (the `.` after
     * `HH:mm:ss` and one-or-more digits). Used to normalize the fraction to exactly 3
     * digits before parsing — ISO 8601 treats the fraction as a decimal of a second
     * (`.5` = 500 ms), but `SimpleDateFormat`'s `S` interprets the digits literally
     * (`.5` = 5 ms).
     */
    private val FRACTIONAL_SECONDS = Regex("""(\d{2}:\d{2}:\d{2})\.(\d+)""")

    /**
     * Matches the zone designator at the end of an ISO 8601 timestamp: `Z`, `±HH:MM` or
     * `±HHMM`. The zone is a suffix, so the match is anchored there and is unique.
     */
    private val ZONE_SUFFIX = Regex("""(Z|[+-]\d{2}:?\d{2})$""")

    /**
     * ISO 8601 output pattern, second precision. The `'Z'` is a literal, not a zone letter:
     * output is pinned to [UTC], so the offset is always zero. `X` renders identically on a
     * modern platform but is rejected below API 24, so it must not be substituted here.
     */
    @VisibleForTesting
    internal const val OUTPUT_PATTERN: String = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    /** [OUTPUT_PATTERN] with fractional seconds. Its `'Z'` is a literal on the same terms. */
    @VisibleForTesting
    internal const val OUTPUT_PATTERN_MILLIS: String = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    /**
     * RFC 7231 HTTP-date patterns: IMF-fixdate (preferred) and the two obsolete forms
     * (RFC 850 and asctime). All are anchored to GMT/UTC.
     */
    @VisibleForTesting
    internal val HTTP_DATE_PATTERNS: List<String> = listOf(
        "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
        "EEEE, dd-MMM-yy HH:mm:ss 'GMT'",
        "EEE MMM d HH:mm:ss yyyy",
    )

    /**
     * ISO 8601 parse patterns, ordered most-specific first. The first format that consumes
     * the entire input wins. Inputs are normalized before parsing — fractional seconds to 3
     * digits, and the zone designator to the `±HHMM` form that `Z` accepts.
     */
    @VisibleForTesting
    internal val ISO_8601_PATTERNS: List<String> = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
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
    )

    // Not routed through formats(): a skipped output pattern leaves nothing to format with.
    private val FORMAT_NO_MILLIS = strictFormat(OUTPUT_PATTERN)

    private val FORMAT_WITH_MILLIS = strictFormat(OUTPUT_PATTERN_MILLIS)

    private val HTTP_DATE_FORMATS = formats(HTTP_DATE_PATTERNS)

    private val PARSE_FORMATS = formats(ISO_8601_PATTERNS)

    private val lock = Any()

    /**
     * Parses an ISO 8601 timestamp into epoch milliseconds.
     *
     * Accepted forms (UTC unless a zone is specified):
     *  - `2024`, `2024-01`, `2024-01-15`
     *  - `2024-01-15T11`, `2024-01-15T11:30`, `2024-01-15T11:30:45` (or with a space separator)
     *  - `2024-01-15T11:30:45.123` (fractional seconds, treated as a decimal of a second
     *    per ISO 8601; truncated to millisecond precision)
     *  - `2024-01-15T11:30:45Z`
     *  - `2024-01-15T11:30:45+00:00` / `+0000` / `-05:00`
     *  - Any combination of the above.
     *
     * A named zone (`2024-01-15T11:30:45PST`) is not an accepted form.
     *
     * @throws ParseException if the timestamp cannot be parsed.
     */
    @Throws(ParseException::class)
    public fun parseIso8601(timeStamp: String?): Long {
        if (timeStamp.isNullOrEmpty()) {
            throw ParseException("Unable to parse null or empty timestamp", -1)
        }
        val normalized = normalizeZoneSuffix(normalizeFractionalSeconds(timeStamp))
        // Normalized, every accepted form ends in a digit, so a trailing letter is a zone name
        // -- which the patterns' `Z` would otherwise accept via its named-zone fallback. An
        // ISO 8601 parse pattern must therefore not end in a quoted literal.
        if (normalized.last().isLetter()) {
            throw ParseException("Unable to parse $timeStamp", -1)
        }
        synchronized(lock) {
            for (format in PARSE_FORMATS) {
                val position = ParsePosition(0)
                val parsed = format.parse(normalized, position)
                if (parsed != null && position.index == normalized.length) {
                    return parsed.time
                }
            }
        }
        throw ParseException("Unable to parse $timeStamp", -1)
    }

    /**
     * Pads or truncates the fractional-seconds component to exactly 3 digits.
     *
     * `SimpleDateFormat`'s `S` is lenient and would otherwise read `.5` as 5 ms rather than
     * the 500 ms ISO 8601 means.
     */
    private fun normalizeFractionalSeconds(input: String): String =
        FRACTIONAL_SECONDS.replace(input) { match ->
            val fraction = match.groupValues[2]
            val padded = when {
                fraction.length >= 3 -> fraction.substring(0, 3)
                else -> fraction.padEnd(3, '0')
            }
            "${match.groupValues[1]}.$padded"
        }

    /**
     * Rewrites a trailing zone designator to the `±HHMM` form, mapping `Z` to `+0000` and
     * dropping the `:` from `±HH:MM`.
     *
     * The patterns use `Z` rather than `X` because `SimpleDateFormat` rejects `X` below
     * API 24, and `Z` accepts only `±HHMM`. An input without a zone is returned unchanged.
     */
    private fun normalizeZoneSuffix(input: String): String =
        ZONE_SUFFIX.replace(input) { match ->
            val zone = match.groupValues[1]
            if (zone == "Z") "+0000" else zone.replace(":", "")
        }

    /**
     * Parses an RFC 7231 HTTP-date into epoch milliseconds.
     *
     * @throws ParseException if the input is null, empty, or not a recognized HTTP-date.
     */
    @Throws(ParseException::class)
    public fun parseHttpDate(timeStamp: String?): Long {
        if (timeStamp.isNullOrEmpty()) {
            throw ParseException("Unable to parse null or empty HTTP-date", -1)
        }
        synchronized(lock) {
            for (format in HTTP_DATE_FORMATS) {
                val position = ParsePosition(0)
                val parsed = format.parse(timeStamp, position)
                if (parsed != null && position.index == timeStamp.length) {
                    return parsed.time
                }
            }
        }
        throw ParseException("Unable to parse HTTP-date $timeStamp", -1)
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

    /** Builds a format, throwing if the platform rejects the pattern. */
    private fun strictFormat(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = UTC }

    /**
     * Builds a [SimpleDateFormat] for each pattern, skipping any the platform rejects.
     *
     * Which pattern letters `SimpleDateFormat` accepts varies by API level, and it throws on
     * one it doesn't know. Skipping costs a single input form; letting it propagate would fail
     * this object's initializer, after which every call here throws `NoClassDefFoundError` for
     * the life of the process. `DateUtilsTest` fails the build if a declared pattern uses a
     * letter the minimum API level rejects, so the list cannot narrow unnoticed.
     *
     * The output formats are the only ones built strictly. Building a parse list that way
     * reinstates the failure, and no unit test can observe which builder a field used.
     *
     * @param patterns The patterns to build, in priority order.
     * @return The formats this platform accepts, in the same order.
     */
    @VisibleForTesting
    internal fun formats(patterns: List<String>): List<SimpleDateFormat> =
        patterns.mapNotNull { pattern ->
            try {
                strictFormat(pattern)
            } catch (e: RuntimeException) {
                // Runs during class initialization, so nothing may escape — not even a log
                // handler the integrator supplied.
                runCatching { UALog.e(e) { "Skipping unsupported date pattern: $pattern" } }
                null
            }
        }
}
