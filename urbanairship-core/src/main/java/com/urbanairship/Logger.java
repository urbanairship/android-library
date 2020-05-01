/* Copyright Airship and Contributors */

package com.urbanairship;

import android.util.Log;

import com.urbanairship.util.UAStringUtil;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Shared logging wrapper for all Airship log entries.
 * This class serves to consolidate the tag and log level in a
 * single location.
 */
public class Logger {

    @NonNull
    static String DEFAULT_TAG = "UALib";

    private static LoggingCore logger = new LoggingCore(Log.ERROR, DEFAULT_TAG);

    /**
     * Private, unused constructor
     */
    private Logger() {
    }

    /**
     * Sets the logger tag.
     *
     * @param tag The tag.
     */
    public static void setTag(@NonNull String tag) {
        logger.setTag(tag);
    }

    /**
     * Sets the log level.
     *
     * @param logLevel The log level.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void setLogLevel(int logLevel) {
        logger.setLogLevel(logLevel);
    }

    /**
     * Gets the log level.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static int getLogLevel() {
        return logger.getLogLevel();
    }

    /**
     * Disables Airship from using the default logger.
     */
    public static void disableDefaultLogger() {
        logger.setDefaultLoggerEnabled(false);
    }

    /**
     * Adds a listener.
     *
     * Listener callbacks are synchronized but will be made from the originating thread.
     * Responsibility for any additional threading guarantees falls on the application.
     *
     * @param listener The listener.
     */
    public static void addListener(@NonNull LoggerListener listener) {
        logger.addListener(listener);
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener.
     */
    public static void removeListener(@NonNull LoggerListener listener) {
        logger.removeListener(listener);
    }

    /**
     * Send a warning log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void warn(@NonNull String message, @Nullable Object... args) {
        logger.log(Log.WARN, null, message, args);
    }

    /**
     * Send a warning log message.
     *
     * @param t An exception to log
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void warn(@NonNull Throwable t, @NonNull String message, @Nullable Object... args) {
        logger.log(Log.WARN, t, message, args);
    }

    /**
     * Send a warning log message.
     *
     * @param t An exception to log
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void warn(@NonNull Throwable t) {
        logger.log(Log.WARN, t, null, (Object[]) null);
    }

    /**
     * Send a verbose log message.
     *
     * @param t An exception to log
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void verbose(@NonNull Throwable t) {
        logger.log(Log.VERBOSE, t, null, (Object[]) null);
    }

    /**
     * Send a verbose log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void verbose(@NonNull String message, @Nullable Object... args) {
        logger.log(Log.VERBOSE, null, message, args);
    }

    /**
     * Send a debug log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void debug(@NonNull String message, @Nullable Object... args) {
        logger.log(Log.DEBUG, null, message, args);
    }

    /**
     * Send a debug log message.
     *
     * @param t An exception to log
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void debug(@NonNull Throwable t, @NonNull String message, @Nullable Object... args) {
        logger.log(Log.DEBUG, t, message, args);
    }

    /**
     * Send an info log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void info(@NonNull String message, @NonNull Object... args) {
        logger.log(Log.INFO, null, message, args);
    }

    /**
     * Send an info log message.
     *
     * @param t An exception to log
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void info(@NonNull Throwable t, @NonNull String message, @Nullable Object... args) {
        logger.log(Log.INFO, t, message, args);
    }

    /**
     * Send an error log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void error(@NonNull String message, @Nullable Object... args) {
        logger.log(Log.ERROR, null, message, args);
    }

    /**
     * Send an error log message.
     *
     * @param t An exception to log
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void error(@NonNull Throwable t) {
        logger.log(Log.ERROR, t, null, (Object[]) null);
    }

    /**
     * Send an error log message.
     *
     * @param t An exception to log
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void error(@NonNull Throwable t, @NonNull String message, @Nullable Object... args) {
        logger.log(Log.ERROR, t, message, args);

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
            int intValue = Integer.parseInt(value);
            if (intValue <= Log.ASSERT && intValue >= Log.VERBOSE) {
                return intValue;
            }

            warn("%s is not a valid log level. Falling back to %s.", intValue, defaultValue);
            return defaultValue;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid log level: " + value);
        }
    }

}
