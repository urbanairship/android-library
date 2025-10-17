/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.IntRange
import androidx.core.content.edit
import com.urbanairship.UALog
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * An auto-incrementing notification ID generator.
 */
public object NotificationIdGenerator {

    private const val SHARED_PREFERENCES_FILE = "com.urbanairship.notificationidgenerator"

    private const val NEXT_ID_KEY = "count"
    private const val MAX_RANGE = 50

    private var start = 1000
    private var range = 40 //Android allows a maximum of 50 notifications per package (undocumented)

    private val lock: ReentrantLock = ReentrantLock()

    private fun preferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
    }


    //implicitly resets the count
    /**
     * Set the count and start value.
     *
     * @param value The integer value
     */
    @JvmStatic
    public fun setStart(context: Context, value: Int) {
        lock.withLock {
            preferences(context).edit {
                putInt(NEXT_ID_KEY, value)
            }
        }
    }

    /**
     * Set the number of notifications to display (max range). Implicitly resets
     * the current id to [.getStart].
     *
     * @param newRange The number of notifications to display
     */
    @JvmStatic
    public fun setRange(context: Context, @IntRange(from = 0, to = MAX_RANGE.toLong()) newRange: Int) {
        lock.withLock {
            var newRange = newRange
            if (newRange > MAX_RANGE) {
                UALog.e(
                    "The maximum number of notifications allowed is %s. Limiting alert id range to conform.",
                    MAX_RANGE
                )
                newRange = MAX_RANGE
            }

            preferences(context).edit {
                putInt(NEXT_ID_KEY, start)
            }

            range = newRange
        }
    }

    /**
     * Get the start value.
     *
     * @return The int start.
     */
    @JvmStatic
    public fun getStart(): Int {
        lock.withLock {
            return start
        }
    }

    /**
     * Get the range.
     *
     * @return The int range.
     */
    @JvmStatic
    public fun getRange(): Int {
        lock.withLock {
            return range
        }
    }

    /**
     * Store the next ID.
     *
     * @param context The application context.
     * @return The int next ID.
     */
    @JvmStatic
    public fun nextId(context: Context): Int {
        lock.withLock {
            val preferences = preferences(context)
            var id = preferences.getInt(NEXT_ID_KEY, start)

            // If we are at the end of the range, reset and write back.
            val nextId = ++id
            if (nextId < start + range) {
                UALog.v("Incrementing notification ID count")
                preferences.edit { putInt(NEXT_ID_KEY, nextId) }
            } else {
                UALog.v("Resetting notification ID count")
                preferences.edit { putInt(NEXT_ID_KEY, start) }
            }

            UALog.v("Notification ID: %s", id)
            return id
        }
    }
}
