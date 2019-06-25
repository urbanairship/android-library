/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import androidx.databinding.ObservableBoolean

/**
 * An event filter.
 */
class EventFilter(val type: String) {

    val isChecked = ObservableBoolean(false)
}
