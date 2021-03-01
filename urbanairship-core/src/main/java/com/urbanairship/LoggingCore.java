/* Copyright Airship and Contributors */

package com.urbanairship;

import android.util.Log;

import com.urbanairship.util.UAStringUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Shared logging wrapper for all log entries.
 * This class serves to consolidate the tag and log level in a
 * single location.
 */
public class LoggingCore {

    /** List of classes to ignore when prepending names to debug and verbose log messages. */
    private static final List<String> IGNORED_CALLING_CLASS_NAMES = Arrays.asList(
            LoggingCore.class.getName(),
            Logger.class.getName()
    );

    private String logTag;
    private int logLevel;
    private boolean isDefaultLoggerEnabled = true;


    /**
     * A list of listeners.
     */
    private final List<LoggerListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * The logging core constructor.
     *
     * @param logLevel The loggin level.
     * @param tag The log tag.
     */
    public LoggingCore(int logLevel, @NonNull String tag) {
        this.logLevel = logLevel;
        this.logTag = tag;
    }

    /**
     * Sets the log tag.
     *
     * @param tag The log tag.
     */
    public void setTag(@NonNull String tag) {
        this.logTag = tag;
    }

    /**
     * Sets the enabled default logger flag.
     *
     * @param enabled The enable default logger flag.
     */
    public void setDefaultLoggerEnabled(boolean enabled) {
        this.isDefaultLoggerEnabled = enabled;
    }

    /**
     * Adds a listener.
     *
     * Listener callbacks are synchronized but will be made from the originating thread.
     * Responsibility for any additional threading guarantees falls on the application.
     *
     * @param listener The listener.
     */
    public void addListener(@NonNull LoggerListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener.
     */
    public void removeListener(@NonNull LoggerListener listener) {
        listeners.remove(listener);
    }

    /**
     * Helper method that performs the logging.
     *
     * @param priority The log priority level.
     * @param throwable The optional exception.
     * @param message The optional message.
     * @param args The optional message args.
     */
    public void log(int priority, @Nullable Throwable throwable, @Nullable String message, @Nullable Object... args) {
        if (logLevel > priority) {
            return;
        }

        if (message == null && throwable == null) {
            return;
        }

        if (priority == Log.DEBUG || priority == Log.VERBOSE) {
            message = prependCallingClassName(message);
        }

        String formattedMessage;

        if (UAStringUtil.isEmpty(message)) {
            // Default to empty string
            formattedMessage = "";
        } else {
            // Format the message if we have arguments
            try {
                formattedMessage = (args == null || args.length == 0) ? message : String.format(Locale.ROOT, message, args);
            } catch (Exception e) {
                formattedMessage = "Unable to format log message: " + message;
                priority = Log.ERROR;
                throwable = e;
            }
        }

        for (LoggerListener listener : listeners) {
            listener.onLog(priority, throwable, formattedMessage);
        }

        if (isDefaultLoggerEnabled) {
            // Log directly if we do not have a throwable
            if (throwable == null) {
                if (priority == Log.ASSERT) {
                    Log.wtf(logTag, formattedMessage);
                } else {
                    Log.println(priority, logTag, formattedMessage);
                }
                return;
            }

            // Log using one of the provided log methods
            switch (priority) {
                case Log.INFO:
                    Log.i(logTag, formattedMessage, throwable);
                    break;
                case Log.DEBUG:
                    Log.d(logTag, formattedMessage, throwable);
                    break;
                case Log.VERBOSE:
                    Log.v(logTag, formattedMessage, throwable);
                    break;
                case Log.WARN:
                    Log.w(logTag, formattedMessage, throwable);
                    break;
                case Log.ERROR:
                    Log.e(logTag, formattedMessage, throwable);
                    break;
                case Log.ASSERT:
                    Log.wtf(logTag, formattedMessage, throwable);
                    break;
            }
        }
    }

    /**
     * Sets the log level.
     *
     * @param logLevel The log priority level.
     */
    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * Gets the log level.
     *
     * @return The log priority level.
     */
    public int getLogLevel() {
        return logLevel;
    }

    private static String prependCallingClassName(String message) {
        if (message == null) {
            return "";
        }

        String callingClassName = getCallingClassName();
        if (callingClassName == null || message.startsWith(callingClassName)) {
            // If the caller's name is somehow null or the message already starts with the name,
            // return the original message.
            return message;
        } else {
            // Otherwise, prepend the caller's name and a dash to the message.
            return callingClassName + " - " + message;
        }
    }

    @Nullable
    private static String getCallingClassName() {
        // Grab a stack trace and look through it until we find an element outside of this class.
        StackTraceElement[] trace = new Throwable().getStackTrace();
        for (int i = 1; i < trace.length; i++) {
            String className = trace[i].getClassName();
            if (!IGNORED_CALLING_CLASS_NAMES.contains(className)) {
                // Get the simple name from the last part of the fully-qualified class name,
                // dropping any anonymous or inner class suffixes.
                String[] parts = className.split("\\.");
                return parts[parts.length - 1].replaceAll("(\\$.+)+$", "");
            }
        }
        return null;
    }
}
