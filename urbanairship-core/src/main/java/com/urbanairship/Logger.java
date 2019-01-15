/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
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
    public static String TAG = "UALib";

    /**
     * A list of listeners.
     */
    private static ArrayList<LoggerListener> listeners = new ArrayList<>();

    /**
     * Private, unused constructor
     */
    private Logger() { }

    /**
     * Adds a listener.
     *
     * @note Listener callbacks are synchronized but will be made from the originating thread.
     * Responsibility for any additional threading guarantees falls on the application.
     *
     * @param listener The listener.
     */
    public static void addListener(@NonNull LoggerListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener.
     */
    public static void removeListener(@NonNull LoggerListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Send a warning log message.
     *
     * @param s The message you would like logged.
     */
    public static void warn(String s) {
        if (logLevel <= Log.WARN && s != null) {
            log(Log.WARN, s);
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
            log(Log.WARN, s, t);
        }
    }

    /**
     * Send a warning log message.
     *
     * @param t An exception to log
     */
    public static void warn(Throwable t) {
        if (logLevel <= Log.WARN && t != null) {
            log(Log.WARN, t);
        }
    }

    /**
     * Send a verbose log message.
     *
     * @param s The message you would like logged.
     */
    public static void verbose(String s) {
        if (logLevel <= Log.VERBOSE && s != null) {
            log(Log.VERBOSE, s);
        }
    }

    /**
     * Send a debug log message.
     *
     * @param s The message you would like logged.
     */
    public static void debug(String s) {
        if (logLevel <= Log.DEBUG && s != null) {
            log(Log.DEBUG, s);
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
            log(Log.DEBUG, s, t);
        }
    }

    /**
     * Send an info log message.
     *
     * @param s The message you would like logged.
     */
    public static void info(String s) {
        if (logLevel <= Log.INFO && s != null) {
            log(Log.INFO, s);
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
            log(Log.INFO, s, t);
        }
    }

    /**
     * Send an error log message.
     *
     * @param s The message you would like logged.
     */
    public static void error(String s) {
        if (logLevel <= Log.ERROR && s != null) {
            log(Log.ERROR, s);
        }
    }

    /**
     * Send an error log message.
     *
     * @param t An exception to log
     */
    public static void error(Throwable t) {
        if (logLevel <= Log.ERROR && t != null) {
            log(Log.ERROR, t);
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
            log(Log.ERROR, s, t);
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

            Logger.warn(intValue + " is not a valid log level. Falling back to " + defaultValue + ".");
            return defaultValue;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid log level: " + value);
        }
    }

    private static void log(int priority, @Nullable Throwable throwable) {
        log(priority, null, throwable);
    }

    private static void log(int priority, @Nullable String message) {
        log(priority, message, null);
    }

    private static void log(int priority, @Nullable String message, @Nullable Throwable throwable) {
        if (logLevel > priority) {
            return;
        }

        String formattedMessage = UAStringUtil.isEmpty(message) ? "" : message;

        // Call through to listeners
        synchronized (listeners) {
            for (LoggerListener listener : new ArrayList<>(listeners)) {
                listener.onLog(priority, throwable, formattedMessage);
            }
        }

        // Log directly if we do not have a throwable
        if (throwable == null) {
            if (priority == Log.ASSERT) {
                Log.wtf(TAG, formattedMessage);
            } else {
                Log.println(priority, TAG, formattedMessage);
            }
            return;
        }

        // Log using one of the provided log methods
        switch (priority) {
            case Log.INFO:
                Log.i(TAG, formattedMessage, throwable);
                break;
            case Log.DEBUG:
                Log.d(TAG, formattedMessage, throwable);
                break;
            case Log.VERBOSE:
                Log.v(TAG, formattedMessage, throwable);
                break;
            case Log.WARN:
                Log.w(TAG, formattedMessage, throwable);
                break;
            case Log.ERROR:
                Log.e(TAG, formattedMessage, throwable);
                break;
            case Log.ASSERT:
                Log.wtf(TAG, formattedMessage, throwable);
                break;
        }
    }
}
