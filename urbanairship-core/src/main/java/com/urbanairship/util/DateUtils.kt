/* Copyright Airship and Contributors */
package com.urbanairship.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.annotation.RestrictTo

/**
 * Date utilities.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object DateUtils {

    private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val ALT_ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val lock = Any()

    init {
        ISO_DATE_FORMAT.timeZone = TimeZone.getTimeZone("UTC")
        ALT_ISO_DATE_FORMAT.timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Parses an ISO 8601 timestamp.
     *
     * @param timeStamp The ISO time stamp string.
     * @return The time in milliseconds since Jan. 1, 1970, midnight GMT or -1 if the timestamp was unable
     * to be parsed.
     * @throws java.text.ParseException if the timestamp was unable to be parsed.
     */
    @Throws(ParseException::class)
    public fun parseIso8601(timeStamp: String?): Long {
        if (timeStamp == null) {
            throw ParseException("Unable to parse null timestamp", -1)
        }

        try {
            synchronized(lock) {
                return try {
                    ISO_DATE_FORMAT.parse(timeStamp)?.time
                } catch (_: ParseException) {
                    ALT_ISO_DATE_FORMAT.parse(timeStamp)?.time
                } ?: throw ParseException("Unable to parse $timeStamp", -1)
            }
        } catch (e: Exception) {
            throw ParseException(
                "Unexpected issue when attempting to parse $timeStamp - ${e.message}", -1
            )
        }
    }

    /**
     * Parses an ISO 8601 timestamp.
     *
     * @param timeStamp The ISO time stamp string.
     * @param defaultValue The default value
     * @return The time in milliseconds since Jan. 1, 1970, midnight GMT or the default value
     * if the timestamp was unable to be parsed.
     */
    public fun parseIso8601(timeStamp: String, defaultValue: Long): Long {
        return try {
            parseIso8601(timeStamp)
        } catch (_: ParseException) {
            defaultValue
        }
    }

    /**
     * Creates an ISO 8601 formatted time stamp.
     *
     * @param milliseconds The time in milliseconds since Jan. 1, 1970, midnight GMT.
     * @return An ISO 8601 formatted time stamp.
     */
    public fun createIso8601TimeStamp(milliseconds: Long): String {
        synchronized(lock) {
            return ISO_DATE_FORMAT.format(Date(milliseconds))
        }
    }
}
