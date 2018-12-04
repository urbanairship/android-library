/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.urbanairship.util.UAStringUtil;

import java.util.Locale;

/**
 * Shared logging wrapper for all Urban Airship log entries.
 * This class serves to consolidate the tag and log level in a
 * single location.
 *
 * @author Urban Airship
 * @see android.util.Log
 */
public class Logger {

    /**
     * The current log level, as defined by <code>android.util.Log</code>.
     * Defaults to <code>android.util.Log.ERROR</code>.
     */
    public static int logLevel = Log.ERROR;

    /**
     * The current log tag.
     * Defaults to "UALib".
     */
    @NonNull
    public static String TAG = "UALib";

    /**
     * Private, unused constructor
     */
    private Logger() { }

    /**
     * Send a warning log message.
     *
     * @param s The message you would like logged.
     */
    public static void warn(@NonNull String s, Object... args) {
        if (logLevel <= Log.WARN) {
            String message = String.format(s, args);
            Log.w(TAG, message);
        }
    }

    /**
     * Send a warning log message.
     *
     * @param t An exception to log
     * @param s The message you would like logged.
     */
    public static void warn(@NonNull Throwable t, @NonNull String s, Object... args) {
        if (logLevel <= Log.WARN) {
            String message = String.format(s, args);
            Log.w(TAG, message, t);
        }
    }

    /**
     * Send a warning log message.
     *
     * @param t An exception to log
     */
    public static void warn(@NonNull Throwable t) {
        if (logLevel <= Log.WARN) {
            Log.w(TAG, t);
        }
    }

    /**
     * Send a verbose log message.
     *
     * @param s The message you would like logged.
     */
    public static void verbose(@NonNull String s, Object... args) {
        if (logLevel <= Log.VERBOSE) {
            String message = String.format(s, args);
            Log.v(TAG, message);
        }
    }

    /**
     * Send a debug log message.
     *
     * @param s The message you would like logged.
     */
    public static void debug(@NonNull String s, Object... args) {
        if (logLevel <= Log.DEBUG) {
            String message = String.format(s, args);
            Log.d(TAG, message);
        }
    }

    /**
     * Send a debug log message.
     *
     * @param t An exception to log
     * @param s The message you would like logged.
     */
    public static void debug(@NonNull Throwable t, @NonNull String s, Object... args) {
        if (logLevel <= Log.DEBUG) {
            String message = String.format(s, args);
            Log.d(TAG, message, t);
        }
    }

    /**
     * Send an info log message.
     *
     * @param s The message you would like logged.
     */
    public static void info(@NonNull String s, Object... args) {
        if (logLevel <= Log.INFO) {
            String message = String.format(s, args);
            Log.i(TAG, message);
        }
    }

    /**
     * Send an info log message.
     *
     * @param t An exception to log
     * @param s The message you would like logged.
     */
    public static void info(@Nullable Throwable t, @NonNull String s, Object... args) {
        if (logLevel <= Log.INFO && t != null) {
            String message = String.format(s, args);
            Log.i(TAG, message, t);
        }
    }

    /**
     * Send an error log message.
     *
     * @param s The message you would like logged.
     */
    public static void error(@NonNull String s, Object... args) {
        if (logLevel <= Log.ERROR) {
            String message = String.format(s, args);
            Log.e(TAG, message);
        }
    }

    /**
     * Send an error log message.
     *
     * @param t An exception to log
     */
    public static void error(@Nullable Throwable t) {
        if (logLevel <= Log.ERROR && t != null) {
            Log.e(TAG, "", t);
        }
    }

    /**
     * Send an error log message.
     *
     * @param t An exception to log
     * @param s The message you would like logged.
     */
    public static void error(@Nullable Throwable t, @NonNull String s, Object... args) {
        if (logLevel <= Log.ERROR && t != null) {
            String message = String.format(s, args);
            Log.e(TAG, message, t);
        }
    }

    /**
     * Parses the log level from a String.
     *
     * @param value The log level as a String.
     * @param defaultValue Default value if the value is empty.
     * @return The log level.
     * @throws IllegalArgumentException if an invalid log level is provided.
     */
    static int parseLogLevel(@Nullable String value, int defaultValue) throws IllegalArgumentException {
        if (UAStringUtil.isEmpty(value)) {
            return defaultValue;
        }

        switch (value.toUpperCase(Locale.ROOT)) {
            case "ASSERT":
            case "NONE":
                return Log.ASSERT;
            case "DEBUG":
                return Log.DEBUG;
            case "ERROR":
                return Log.ERROR;
            case "INFO":
                return Log.INFO;
            case "VERBOSE":
                return Log.VERBOSE;
            case "WARN":
                return Log.WARN;
        }

        try {
            int intValue = Integer.valueOf(value);
            if (intValue <= Log.ASSERT && intValue >= Log.VERBOSE) {
                return intValue;
            }

            Logger.warn("%s is not a valid log level. Falling back to %s.", intValue, defaultValue);
            return defaultValue;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid log level: " + value);
        }
    }
}
