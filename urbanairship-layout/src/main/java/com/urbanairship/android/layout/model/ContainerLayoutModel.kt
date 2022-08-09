/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.model.BaseModel.Companion.backgroundColorFromJson
import com.urbanairship.android.layout.model.BaseModel.Companion.borderFromJson
import com.urbanairship.android.layout.model.SafeAreaAware.Companion.ignoreSafeAreaFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Margin
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap

internal class ContainerLayoutModel(
    val items: List<Item>,
    border: Border?,
    backgroundColor: Color?
) : LayoutModel(ViewType.CONTAINER, backgroundColor, border) {

    override val children: List<BaseModel> = items.map { it.view }

    init {
        for (item in items) {
            item.view.addListener(this)
        }
    }

    class Item(
        val position: Position,
        val size: Size,
        val view: BaseModel,
        val margin: Margin?,
        private val ignoreSafeArea: Boolean
    ) : SafeAreaAware {

        override fun shouldIgnoreSafeArea(): Boolean = ignoreSafeArea

        companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): Item {
                val positionJson = json.opt("position").optMap()
                val sizeJson = json.opt("size").optMap()
                val viewJson = json.opt("view").optMap()
                val marginJson = json.opt("margin").optMap()
                return Item(
                    position = Position.fromJson(positionJson),
                    size = Size.fromJson(sizeJson),
                    view = Thomas.model(viewJson),
                    margin = if (marginJson.isEmpty) null else Margin.fromJson(marginJson),
                    ignoreSafeArea = ignoreSafeAreaFromJson(json)
                )
            }

            @Throws(JsonException::class)
            fun fromJsonList(json: JsonList): List<Item> =
                json.list.map { itemJson ->
                    fromJson(itemJson.optMap())
                }
        }
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): ContainerLayoutModel {
            val itemsJson = json.opt("items").optList()
            return ContainerLayoutModel(
                items = Item.fromJsonList(itemsJson),
                border = borderFromJson(json),
                backgroundColor = backgroundColorFromJson(json)
            )
        }
    }
}
