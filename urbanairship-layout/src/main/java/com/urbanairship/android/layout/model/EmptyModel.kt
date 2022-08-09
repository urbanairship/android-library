/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.view.EmptyView
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

/**
 * An empty view that can have a background and border.
 * Useful for the nub on a banner or as dividers between items.
 *
 * @see EmptyView
 */
internal class EmptyModel(
    backgroundColor: Color?,
    border: Border?
) : BaseModel(ViewType.EMPTY_VIEW, backgroundColor, border) {

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): EmptyModel =
            EmptyModel(
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
    }
}
