/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField

/** How one linear layout item is placed among its siblings. */
internal data class LinearLayoutItemConfig(
    /**
     * Where the item falls among its siblings: items lay out, and are read, in ascending order, in
     * list order among equal ones. After a `randomize_children` shuffle, so a "None of these" at
     * order 1 stays last.
     */
    val order: Double = 0.0
) {

    companion object {
        fun fromJson(json: JsonMap): LinearLayoutItemConfig = LinearLayoutItemConfig(
            order = json.optionalField<Double>("order") ?: 0.0
        )
    }
}
