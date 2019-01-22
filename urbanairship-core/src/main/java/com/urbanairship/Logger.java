/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.util.Log;

import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

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
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static int logLevel = Log.ERROR;

    /**
     * The current log tag.
     * Defaults to "UALib".
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static String TAG = "UALib";

    /**
     * A list of listeners.
     */
    private static final List<LoggerListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Default logger.
     */
    private static final LoggerListener defaultLogger = new LoggerListener() {
        @Override
        public void onLog(int priority, @Nullable Throwable throwable, @Nullable String message) {
            // Log directly if we do not have a throwable
            if (throwable == null) {
                if (priority == Log.ASSERT) {
                    Log.wtf(TAG, message);
                } else {
                    Log.println(priority, TAG, message);
                }
                return;
            }

            // Log using one of the provided log methods
            switch (priority) {
                case Log.INFO:
                    Log.i(TAG, message, throwable);
                    break;
                case Log.DEBUG:
                    Log.d(TAG, message, throwable);
                    break;
                case Log.VERBOSE:
                    Log.v(TAG, message, throwable);
                    break;
                case Log.WARN:
                    Log.w(TAG, message, throwable);
                    break;
                case Log.ERROR:
                    Log.e(TAG, message, throwable);
                    break;
                case Log.ASSERT:
                    Log.wtf(TAG, message, throwable);
                    break;
            }
        }
    };

    static {
        listeners.add(defaultLogger);
    }

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
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener.
     */
    public static void removeListener(@NonNull LoggerListener listener) {
        listeners.remove(listener);
    }

    /**
     * Disables Urban Airship from using the standard {@link Log} for logging.
     */
    public static void disableDefaultLogger() {
        removeListener(defaultLogger);
    }

    /**
     * Send a warning log message.
     *
     * @param s The message you would like logged.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void warn(String s) {
        log(Log.WARN, s);
    }

    /**
     * Send a warning log message.
     *
     * @param s The message you would like logged.
     * @param t An exception to log
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void warn(String s, Throwable t) {
        log(Log.WARN, s, t);
    }

    /**
     * Send a warning log message.
     *
     * @param t An exception to log
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void warn(Throwable t) {
        log(Log.WARN, t);
    }

    /**
     * Send a verbose log message.
     *
     * @param s The message you would like logged.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void verbose(String s) {
        log(Log.VERBOSE, s);
    }

    /**
     * Send a debug log message.
     *
     * @param s The message you would like logged.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void debug(String s) {
        log(Log.DEBUG, s);
    }

    /**
     * Send a debug log message.
     *
     * @param s The message you would like logged.
     * @param t An exception to log
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void debug(String s, Throwable t) {
        log(Log.DEBUG, s, t);
    }

    /**
     * Send an info log message.
     *
     * @param s The message you would like logged.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void info(String s) {
        log(Log.INFO, s);
    }

    /**
     * Send an info log message.
     *
     * @param s The message you would like logged.
     * @param t An exception to log
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void info(String s, Throwable t) {
        log(Log.INFO, s, t);
    }

    /**
     * Send an error log message.
     *
     * @param s The message you would like logged.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void error(String s) {
        log(Log.ERROR, s);
    }

    /**
     * Send an error log message.
     *
     * @param t An exception to log
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void error(Throwable t) {
        log(Log.ERROR, t);
    }

    /**
     * Send an error log message.
     *
     * @param s The message you would like logged.
     * @param t An exception to log
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void error(String s, Throwable t) {
        log(Log.ERROR, s, t);
    }

    /**
     * Parses the log level from a String.
     *
     * @param value The log level as a String.
     * @param defaultValue Default value if the value is empty.
     * @return The log level.
     * @throws IllegalArgumentException
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
        if (message == null && throwable == null) {
            return;
        }

        if (logLevel > priority) {
            return;
        }

        String formattedMessage = UAStringUtil.isEmpty(message) ? "" : message;

        // Call through to listeners
        for (LoggerListener listener : listeners) {
            listener.onLog(priority, throwable, formattedMessage);
        }
    }
}
