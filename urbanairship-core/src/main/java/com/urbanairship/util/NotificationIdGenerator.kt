/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.IntRange
import com.urbanairship.UALog
import com.urbanairship.Airship

/**
 * An auto-incrementing notification ID generator.
 */
public object NotificationIdGenerator {

    private const val SHARED_PREFERENCES_FILE = "com.urbanairship.notificationidgenerator"

    private const val NEXT_ID_KEY = "count"
    private const val MAX_RANGE = 50

    private var start = 1000
    private var range = 40 //Android allows a maximum of 50 notifications per package (undocumented)

    private val preferences: SharedPreferences
        get() = Airship
            .applicationContext
            .getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)


    private fun putInt(key: String, value: Int) {
        preferences
            .edit()
            .also { it.putInt(key, value) }
            .apply()
    }

    private fun getInt(key: String, defValue: Int): Int {
        return preferences.getInt(key, defValue)
    }

    //implicitly resets the count
    /**
     * Set the count and start value.
     *
     * @param value The integer value
     */
    public fun setStart(value: Int) {
        putInt(NEXT_ID_KEY, value)
        start = value
    }

    //implicitly resets the count
    /**
     * Set the number of notifications to display (max range). Implicitly resets
     * the current id to [.getStart].
     *
     * @param newRange The number of notifications to display
     */
    public fun setRange(@IntRange(from = 0, to = MAX_RANGE.toLong()) newRange: Int) {
        var newRange = newRange
        if (newRange > MAX_RANGE) {
            UALog.e(
                "The maximum number of notifications allowed is %s. Limiting alert id range to conform.",
                MAX_RANGE
            )
            newRange = MAX_RANGE
        }

        putInt(NEXT_ID_KEY, start)
        range = newRange
    }

    /**
     * Get the start value.
     *
     * @return The int start.
     */
    public fun getStart(): Int {
        return start
    }

    /**
     * Get the range.
     *
     * @return The int range.
     */
    public fun getRange(): Int {
        return range
    }

    /**
     * Store the next ID.
     *
     * @return The int next ID.
     */
    @JvmStatic
    public fun nextID(): Int {
        //get the next id from the shared prefs

        var id = getInt(NEXT_ID_KEY, start)

        //and write the next value back out

        //store a new next id: increment by one, unless we're already at the maximum
        val nextId = ++id
        if (nextId < start + range) {
            UALog.v("Incrementing notification ID count")
            putInt(NEXT_ID_KEY, nextId)
        } else {
            UALog.v("Resetting notification ID count")
            putInt(NEXT_ID_KEY, start)
        }

        UALog.v("Notification ID: %s", id)

        return id
    }
}
