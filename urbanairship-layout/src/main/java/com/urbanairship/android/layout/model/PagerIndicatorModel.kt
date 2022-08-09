/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import androidx.annotation.Dimension
import com.urbanairship.Logger
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.PagerEvent
import com.urbanairship.android.layout.event.PagerEvent.IndicatorInit
import com.urbanairship.android.layout.event.PagerEvent.Scroll
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.shape.Shape
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal class PagerIndicatorModel(
    val bindings: Bindings,
    @get:Dimension(unit = Dimension.DP)
    val indicatorSpacing: Int,
    backgroundColor: Color?,
    border: Border?
) : BaseModel(ViewType.PAGER_INDICATOR, backgroundColor, border) {

    final var size = -1
        private set

    final var position = -1
        private set

    private var listener: Listener? = null
    private val indicatorViewIds = HashMap<Int, Int>()

    /** Returns a stable viewId for the indicator view at the given `position`.  */
    fun getIndicatorViewId(position: Int): Int =
        indicatorViewIds.getOrPut(position) { View.generateViewId() }

    interface Listener {
        fun onInit(size: Int, position: Int)
        fun onUpdate(position: Int)
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
        if (listener != null && size != -1 && position != -1) {
            listener.onInit(size, position)
        }
    }

    fun onConfigured() {
        bubbleEvent(IndicatorInit(this), LayoutData.empty())
    }

    override fun onEvent(event: Event, layoutData: LayoutData): Boolean {
        Logger.verbose("onEvent: $event layoutData: $layoutData")
        when (event.type) {
            EventType.PAGER_INIT -> if (onPagerInit(event as PagerEvent.Init)) {
                return true
            }
            EventType.PAGER_SCROLL -> if (onPagerScroll(event as Scroll)) {
                return true
            }
            else -> Unit // Do nothing
        }
        return super.onEvent(event, layoutData)
    }

    private fun onPagerInit(event: PagerEvent.Init): Boolean {
        // Set the size and current position from the event data.
        size = event.size
        position = event.pageIndex
        listener?.onInit(size, position)
        return true
    }

    private fun onPagerScroll(event: Scroll): Boolean {
        // Update the current position from the event data.
        position = event.pageIndex
        listener?.onUpdate(position)
        return true
    }

    data class Bindings(val selected: Binding, val unselected: Binding) {
        companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): Bindings {
                val selectedJson = json.opt("selected").optMap()
                val unselectedJson = json.opt("unselected").optMap()
                return Bindings(
                    selected = Binding.fromJson(selectedJson),
                    unselected = Binding.fromJson(unselectedJson)
                )
            }
        }
    }

    data class Binding(val shapes: List<Shape>, val icon: Image.Icon?) {
        companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): Binding {
                val shapes = json.opt("shapes").optList().list.map { Shape.fromJson(it.optMap()) }
                val iconJson = json.opt("icon").optMap()
                val icon = if (iconJson.isEmpty) null else Image.Icon.fromJson(iconJson)
                return Binding(shapes = shapes, icon = icon)
            }
        }
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): PagerIndicatorModel {
            val bindingsJson = json.opt("bindings").optMap()
            return PagerIndicatorModel(
                bindings = Bindings.fromJson(bindingsJson),
                indicatorSpacing = json.opt("spacing").getInt(4),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
        }
    }
}
