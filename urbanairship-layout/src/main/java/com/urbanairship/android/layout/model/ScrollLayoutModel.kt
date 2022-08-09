/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal class ScrollLayoutModel(
    val view: BaseModel,
    val direction: Direction,
    backgroundColor: Color?,
    border: Border?
) : LayoutModel(ViewType.SCROLL_LAYOUT, backgroundColor, border) {

    override val children: List<BaseModel> = listOf(view)

    init {
        view.addListener(this)
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): ScrollLayoutModel {
            val viewJson = json.opt("view").optMap()
            val directionString = json.opt("direction").optString()
            return ScrollLayoutModel(
                view = Thomas.model(viewJson),
                direction = Direction.from(directionString),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
        }
    }
}
