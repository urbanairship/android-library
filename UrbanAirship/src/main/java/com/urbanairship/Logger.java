/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship;

import android.util.Log;

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
}
