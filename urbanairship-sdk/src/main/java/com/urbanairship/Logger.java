/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.util.Log;

import com.urbanairship.util.UAStringUtil;

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
    public static void warn(String s) {
        if (logLevel <= Log.WARN && s != null) {
            Log.w(TAG, s);
        }
    }

    /**
     * Send a warning log message.
     *
     * @param s The message you would like logged.
     * @param t An exception to log
     */
    public static void warn(String s, Throwable t) {
        if (logLevel <= Log.WARN && s != null && t != null) {
            Log.w(TAG, s, t);
        }
    }

    /**
     * Send a warning log message.
     *
     * @param t An exception to log
     */
    public static void warn(Throwable t) {
        if (logLevel <= Log.WARN && t != null) {
            Log.w(TAG, t);
        }
    }

    /**
     * Send a verbose log message.
     *
     * @param s The message you would like logged.
     */
    public static void verbose(String s) {
        if (logLevel <= Log.VERBOSE && s != null) {
            Log.v(TAG, s);
        }
    }

    /**
     * Send a debug log message.
     *
     * @param s The message you would like logged.
     */
    public static void debug(String s) {
        if (logLevel <= Log.DEBUG && s != null) {
            Log.d(TAG, s);
        }
    }

    /**
     * Send a debug log message.
     *
     * @param s The message you would like logged.
     * @param t An exception to log
     */
    public static void debug(String s, Throwable t) {
        if (logLevel <= Log.DEBUG && s != null && t != null) {
            Log.d(TAG, s, t);
        }
    }

    /**
     * Send an info log message.
     *
     * @param s The message you would like logged.
     */
    public static void info(String s) {
        if (logLevel <= Log.INFO && s != null) {
            Log.i(TAG, s);
        }
    }

    /**
     * Send an info log message.
     *
     * @param s The message you would like logged.
     * @param t An exception to log
     */
    public static void info(String s, Throwable t) {
        if (logLevel <= Log.INFO && s != null && t != null) {
            Log.i(TAG, s, t);
        }
    }

    /**
     * Send an error log message.
     *
     * @param s The message you would like logged.
     */
    public static void error(String s) {
        if (logLevel <= Log.ERROR && s != null) {
            Log.e(TAG, s);
        }
    }

    /**
     * Send an error log message.
     *
     * @param t An exception to log
     */
    public static void error(Throwable t) {
        if (logLevel <= Log.ERROR && t != null) {
            Log.e(TAG, "", t);
        }
    }

    /**
     * Send an error log message.
     *
     * @param s The message you would like logged.
     * @param t An exception to log
     */
    public static void error(String s, Throwable t) {
        if (logLevel <= Log.ERROR && s != null && t != null) {
            Log.e(TAG, s, t);
        }
    }

    /**
     * Parses the log level from a String.
     *
     * @param value The log level as a String.
     * @param defaultValue Default value if the value is empty.
     * @return The log level.
     * @throws IllegalArgumentException
     */
    static int parseLogLevel(String value, int defaultValue) throws IllegalArgumentException {
        if (UAStringUtil.isEmpty(value)) {
            return defaultValue;
        }

        switch (value.toUpperCase()) {
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

            Logger.error(intValue + " is not a valid log level. Falling back to " + defaultValue + ".");
            return defaultValue;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid log level: " + value);
        }
    }
}
