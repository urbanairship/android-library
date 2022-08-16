/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.Logger
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.PagerEvent
import com.urbanairship.android.layout.event.PagerEvent.Scroll
import com.urbanairship.android.layout.info.PagerInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue

internal class PagerModel(
    final val items: List<Item>,
    val isSwipeDisabled: Boolean = false,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : LayoutModel<PagerInfo>(
    ViewType.PAGER,
    backgroundColor,
    border,
    environment
) {
    constructor(info: PagerInfo, env: ModelEnvironment) : this(
        info.items.map { Item(it, env) },
        info.isSwipeDisabled,
        info.backgroundColor,
        info.border,
        env
    )

    /** Stable viewId for the recycler view.  */
    val recyclerViewId = View.generateViewId()

    override val children: List<BaseModel> = items.map { it.view }

    private val pageViewIds = mutableMapOf<Int, Int>()

    private var listener: Listener? = null
    private var lastIndex = 0

    init {
        for (c in children) {
            c.addListener(this)
        }
    }

    /** Returns a stable viewId for the pager item view at the given adapter `position`.  */
    fun getPageViewId(position: Int): Int =
        pageViewIds.getOrPut(position) { View.generateViewId() }

    //
    // View Listener
    //

    interface Listener {
        fun onScrollToNext()
        fun onScrollToPrevious()
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    //
    // View Actions
    //

    fun onScrollTo(position: Int, isInternalScroll: Boolean, time: Long) {
        // Bail if this is a duplicate scroll event, which can sometimes happen if
        // the user starts scrolling and then lets go without changing pages after a recreate.
        if (position == lastIndex) return

        val item = items[position]
        bubbleEvent(
            Scroll(
                this,
                position,
                item.identifier,
                item.actions,
                lastIndex,
                items[lastIndex].identifier,
                isInternalScroll,
                time
            ),
            LayoutData.empty()
        )

        lastIndex = position
    }

    fun onConfigured(position: Int, time: Long) {
        val item = items[position]
        bubbleEvent(
            PagerEvent.Init(this, position, item.identifier, item.actions, time),
            LayoutData.empty()
        )
    }

    //
    // Events
    //

    override fun onEvent(event: Event, layoutData: LayoutData): Boolean {
        Logger.verbose("onEvent: $event, layoutData: $layoutData")
        return onEvent(event, layoutData, true)
    }

    private fun onEvent(event: Event, layoutData: LayoutData, bubbleIfUnhandled: Boolean): Boolean {
        return when (event.type) {
            EventType.BUTTON_BEHAVIOR_PAGER_NEXT -> {
                listener?.onScrollToNext()
                true
            }
            EventType.BUTTON_BEHAVIOR_PAGER_PREVIOUS -> {
                listener?.onScrollToPrevious()
                true
            }
            else -> bubbleIfUnhandled && super.onEvent(event, layoutData)
        }
    }

    override fun trickleEvent(event: Event, layoutData: LayoutData): Boolean =
        if (onEvent(event, layoutData, false)) {
            true
        } else {
            super.trickleEvent(event, layoutData)
        }

    class Item(
        val view: BaseModel,
        val identifier: String,
        val actions: Map<String, JsonValue>
    ) {
       constructor(info: PagerInfo.ItemInfo, env: ModelEnvironment) : this(
            env.modelProvider.create(info.info, env),
            info.identifier,
            info.actions
        )
    }
}
