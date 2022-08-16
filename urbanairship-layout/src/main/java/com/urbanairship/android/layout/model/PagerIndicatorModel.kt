/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import androidx.annotation.Dimension
import com.urbanairship.Logger
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.PagerEvent
import com.urbanairship.android.layout.event.PagerEvent.IndicatorInit
import com.urbanairship.android.layout.event.PagerEvent.Scroll
import com.urbanairship.android.layout.info.PagerIndicatorInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData

internal class PagerIndicatorModel(
    val bindings: PagerIndicatorInfo.Bindings,
    @get:Dimension(unit = Dimension.DP)
    val indicatorSpacing: Int,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : BaseModel(
    viewType = ViewType.PAGER_INDICATOR,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
) {
    constructor(info: PagerIndicatorInfo, env: ModelEnvironment) : this(
        info.bindings,
        info.indicatorSpacing,
        info.backgroundColor,
        info.border,
        env
    )

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
        if (size != -1 && position != -1) {
            listener?.onInit(size, position)
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
}
