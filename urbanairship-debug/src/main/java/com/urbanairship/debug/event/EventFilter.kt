/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.event

import android.databinding.ObservableBoolean

/**
 * An event filter.
 */
class EventFilter(val type: String) {

    val isChecked = ObservableBoolean(false)
}
