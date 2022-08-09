/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.Margin
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap

internal class LinearLayoutModel(
    val direction: Direction,
    val items: List<Item>,
    backgroundColor: Color?,
    border: Border?
) : LayoutModel(ViewType.LINEAR_LAYOUT, backgroundColor, border) {

    override val children: List<BaseModel> = items.map { it.view }

    init {
        for (item in items) {
            item.view.addListener(this)
        }
    }

    class Item(val view: BaseModel, val size: Size, val margin: Margin?) {

        companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): Item {
                val viewJson = json.opt("view").optMap()
                val sizeJson = json.opt("size").optMap()
                val marginJson = json.opt("margin").optMap()
                return Item(
                    view = Thomas.model(viewJson),
                    size = Size.fromJson(sizeJson),
                    margin = if (marginJson.isEmpty) null else Margin.fromJson(marginJson)
                )
            }

            @Throws(JsonException::class)
            fun fromJsonList(json: JsonList, randomize: Boolean): List<Item> =
                json.list.map { itemJson ->
                    fromJson(itemJson.optMap())
                }.apply {
                    if (randomize) shuffled()
                }
        }
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): LinearLayoutModel {
            val directionString = json.opt("direction").optString()
            val itemsJson = json.opt("items").optList()
            val randomizeChildren = json.opt("randomize_children").getBoolean(false)
            return LinearLayoutModel(
                direction = Direction.from(directionString),
                items = Item.fromJsonList(itemsJson, randomizeChildren),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
        }
    }
}
