/* Copyright Airship and Contributors */
package com.urbanairship

import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipConfigOptions.PrivacyLevel
import java.util.Locale

/**
 * Shared logging wrapper for all Airship log entries.
 * This class serves to consolidate the tag and log level in a
 * single location.
 */
@Keep
public object UALog {

    /** List of classes to ignore when prepending names to debug and verbose log messages.  */
    private val IGNORED_CALLING_CLASS_NAMES = listOf(
        UALog::class.java.name
    )

    /**
     * Default tag.
     */
    internal const val DEFAULT_TAG: String = "UALib"

    private val EMPTY: () -> String = { "" }

    /**
     * The logger's tag.
     */
    @JvmStatic
    public var tag: String = DEFAULT_TAG

    /**
     * The log level
     */
    @JvmStatic
    public var logLevel: Int = Log.ERROR

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @JvmStatic
    public var logPrivacyLevel: PrivacyLevel = PrivacyLevel.PRIVATE

    /**
     * The log handler.
     */
    @JvmStatic
    public var logHandler: AirshipLogHandler? = DefaultLogHandler()

    /**
     * Send a warning log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     */
    @JvmStatic
    public fun w(message: String, vararg args: Any?) {
        sendLog(Log.WARN, null, format(message, *args))
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
    @JvmStatic
    public fun w(t: Throwable, message: String, vararg args: Any?) {
        sendLog(Log.WARN, t, format(message, *args))
    }

    /**
     * Send a warning log message.
     *
     * @param t An exception to log
     */
    @JvmStatic
    public fun w(t: Throwable) {
        sendLog(Log.WARN, t, EMPTY)
    }

    /**
     * Send a verbose log message.
     *
     * @param t An exception to log
     */
    @JvmStatic
    public fun v(t: Throwable) {
        sendLog(Log.VERBOSE, t, EMPTY)
    }

    /**
     * Send a verbose log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     */
    @JvmStatic
    public fun v(message: String, vararg args: Any?) {
        sendLog(Log.VERBOSE, null, format(message, *args))
    }

    /**
     * Send a debug log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     */
    @JvmStatic
    public fun d(message: String, vararg args: Any?) {
        sendLog(Log.DEBUG, null, format(message, *args))
    }

    /**
     * Send a debug log message.
     *
     * @param t An exception to log
     * @param message The message you would like logged.
     * @param args The message args.
     */
    @JvmStatic
    public fun d(t: Throwable, message: String, vararg args: Any?) {
        sendLog(Log.DEBUG, t, format(message, *args))
    }

    /**
     * Send an info log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     */
    @JvmStatic
    public fun i(message: String, vararg args: Any) {
        sendLog(Log.INFO, null, format(message, *args))
    }

    /**
     * Send an info log message.
     *
     * @param t An exception to log
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @JvmStatic
    public fun i(t: Throwable, message: String, vararg args: Any?) {
        sendLog(Log.INFO, t, format(message, *args))
    }

    /**
     * Send an error log message.
     *
     * @param message The message you would like logged.
     * @param args The message args.
     * @hide
     */
    @JvmStatic
    public fun e(message: String, vararg args: Any?) {
        sendLog(Log.ERROR, null, format(message, *args))
    }

    /**
     * Send an error log message.
     *
     * @param t An exception to log
     */
    @JvmStatic
    public fun e(t: Throwable) {
        sendLog(Log.ERROR, t, EMPTY)
    }

    /**
     * Send an error log message.
     *
     * @param t An exception to log
     * @param message The message you would like logged.
     * @param args The message args.
     */
    @JvmStatic
    public fun e(t: Throwable, message: String, vararg args: Any?) {
        sendLog(Log.ERROR, t, format(message, *args))
    }

    /**
     * Sends a verbose log.
     * @param throwable Optional throwable.
     * @param message The message.
     */
    @JvmStatic
    public fun v(throwable: Throwable? = null, message: () -> String) {
        sendLog(Log.VERBOSE, throwable, message)
    }

    /**
     * Sends a debug log.
     * @param throwable Optional throwable.
     * @param message The message.
     */
    @JvmStatic
    public fun d(throwable: Throwable? = null, message: () -> String) {
        sendLog(Log.DEBUG, throwable, message)
    }

    /**
     * Sends a info log.
     * @param throwable Optional throwable.
     * @param message The message.
     */
    @JvmStatic
    public fun i(throwable: Throwable? = null, message: () -> String) {
        sendLog(Log.INFO, throwable, message)
    }

    /**
     * Sends a warn log.
     * @param throwable Optional throwable.
     * @param message The message.
     */
    @JvmStatic
    public fun w(throwable: Throwable? = null, message: () -> String) {
        sendLog(Log.WARN, throwable, message)
    }

    /**
     * Sends a error log.
     * @param throwable Optional throwable.
     * @param message The message.
     */
    @JvmStatic
    public fun e(throwable: Throwable? = null, message: () -> String) {
        sendLog(Log.ERROR, throwable, message)
    }

    /**
     * Sends a log.
     * @param logLevel The log level.
     * @param throwable Optional throwable.
     * @param message The message.
     */
    @JvmStatic
    public fun log(logLevel: Int, throwable: Throwable? = null, message: () -> String) {
        sendLog(logLevel, throwable, message)
    }

    /**
     * Parses the log level from a String.
     *
     * @param value The log level as a String.
     * @param defaultValue Default value if the value is empty.
     * @return The log level.
     * @throws IllegalArgumentException if an invalid log level is provided.
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    public fun parseLogLevel(value: String?, defaultValue: Int): Int {
        if (value.isNullOrEmpty()) {
            return defaultValue
        }

        when (value.uppercase()) {
            "ASSERT", "NONE" -> return Log.ASSERT
            "DEBUG" -> return Log.DEBUG
            "ERROR" -> return Log.ERROR
            "INFO" -> return Log.INFO
            "VERBOSE" -> return Log.VERBOSE
            "WARN" -> return Log.WARN
        }
        return try {
            val intValue = value.toInt()
            if (intValue <= Log.ASSERT && intValue >= Log.VERBOSE) {
                return intValue
            }
            w("%s is not a valid log level. Falling back to %s.", intValue, defaultValue)
            defaultValue
        } catch (nfe: NumberFormatException) {
            throw IllegalArgumentException("Invalid log level: $value")
        }
    }

    private fun sendLog(logLevel: Int, throwable: Throwable?, message: () -> String) {
        if (this.logLevel > logLevel) {
            return
        }

        val handler = logHandler ?: return

        val actualMessage = if (logLevel == Log.VERBOSE || logLevel == Log.DEBUG) {
            prependCallingClassName(message)
        } else {
            message
        }

        if (this.logPrivacyLevel == PrivacyLevel.PUBLIC && (logLevel == Log.VERBOSE || logLevel == Log.DEBUG)) {
            handler.log(tag, Log.INFO, throwable, actualMessage)
        } else {
            handler.log(tag, logLevel, throwable, actualMessage)
        }

    }

    private fun prependCallingClassName(message: () -> String): () -> String {
        val callingClassName = callingClassName ?: return message

        return {
            val unwrapped = message()
            if (unwrapped.startsWith(callingClassName)) {
                // If the caller's name is somehow null or the message already starts with the name,
                // return the original message.
                unwrapped
            } else {
                // Otherwise, prepend the caller's name and a dash to the message.
                "$callingClassName - $unwrapped"
            }
        }
    }

    private fun format(format: String, vararg args: Any?): () -> String {
        return {
            try {
                if (args.isEmpty()) {
                    format
                } else {
                    String.format(
                        Locale.ROOT, format, *args
                    )
                }
            } catch (e: Exception) {
                e("Unable to format log message: $format")
                format
            }
        }
    }

    private val callingClassName: String?
        get() {
            val trace = Throwable().stackTrace
            for (i in 1 until trace.size) {
                val className = trace[i].className
                if (!IGNORED_CALLING_CLASS_NAMES.contains(className)) {
                    // Get the simple name from the last part of the fully-qualified class name,
                    // dropping any anonymous or inner class suffixes.
                    val parts = className.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    return parts[parts.size - 1].replace("(\\$.+)+$".toRegex(), "")
                }
            }
            return null
        }
}
