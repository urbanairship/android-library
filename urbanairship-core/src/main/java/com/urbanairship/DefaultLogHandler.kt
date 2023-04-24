package com.urbanairship

import android.util.Log

/**
 * Default log handling. Sends all logs to `android.util.Log`.
 */
public class DefaultLogHandler : LogHandler {

    override fun log(tag: String, logLevel: Int, throwable: Throwable?, message: () -> String) {
        if (throwable == null) {
            if (logLevel == Log.ASSERT) {
                Log.wtf(tag, message())
            } else {
                Log.println(logLevel, tag, message())
            }
            return
        }
        when (logLevel) {
            Log.INFO -> Log.i(tag, message(), throwable)
            Log.DEBUG -> Log.d(tag, message(), throwable)
            Log.VERBOSE -> Log.v(tag, message(), throwable)
            Log.WARN -> Log.w(tag, message(), throwable)
            Log.ERROR -> Log.e(tag, message(), throwable)
            Log.ASSERT -> Log.wtf(tag, message(), throwable)
        }
    }
}
