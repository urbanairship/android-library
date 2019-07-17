/* Copyright Airship and Contributors */

package com.urbanairship.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Date utilities.
 */
public class DateUtils {

    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private static final SimpleDateFormat ALT_ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final Object lock = new Object();

    static {
        ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        ALT_ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private DateUtils() { }

    /**
     * Parses an ISO 8601 timestamp.
     *
     * @param timeStamp The ISO time stamp string.
     * @return The time in milliseconds since Jan. 1, 1970, midnight GMT or -1 if the timestamp was unable
     * to be parsed.
     * @throws java.text.ParseException if the timestamp was unable to be parsed.
     */
    public static long parseIso8601(@Nullable String timeStamp) throws ParseException {
        //noinspection ConstantConditions
        if (timeStamp == null) {
            throw new ParseException("Unable to parse null timestamp", -1);
        }

        try {
            synchronized (lock) {
                try {
                    return ISO_DATE_FORMAT.parse(timeStamp).getTime();
                } catch (ParseException ignored) {
                    return ALT_ISO_DATE_FORMAT.parse(timeStamp).getTime();
                }
            }
        } catch (Exception e) {
            throw new ParseException("Unexpected issue when attempting to parse " + timeStamp + " - " + e.getMessage(), -1);
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
    public static long parseIso8601(@NonNull String timeStamp, long defaultValue) {
        try {
            return parseIso8601(timeStamp);
        } catch (ParseException ignored) {
            return defaultValue;
        }
    }

    /**
     * Creates an ISO 8601 formatted time stamp.
     *
     * @param milliseconds The time in milliseconds since Jan. 1, 1970, midnight GMT.
     * @return An ISO 8601 formatted time stamp.
     */
    @NonNull
    public static String createIso8601TimeStamp(long milliseconds) {
        synchronized (lock) {
            return ISO_DATE_FORMAT.format(new Date(milliseconds));
        }
    }

}
