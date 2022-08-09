/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.Logger
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.PagerEvent
import com.urbanairship.android.layout.event.PagerEvent.Scroll
import com.urbanairship.android.layout.model.Identifiable.Companion.identifierFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.testing.OpenForTesting
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

@OpenForTesting
internal class PagerModel(
    val items: List<Item>,
    val isSwipeDisabled: Boolean,
    backgroundColor: Color?,
    border: Border?
) : LayoutModel(ViewType.PAGER, backgroundColor, border) {
    /** Stable viewId for the recycler view.  */
    val recyclerViewId = View.generateViewId()

    override val children: List<BaseModel> = items.map { it.view }

    private val pageViewIds = mutableMapOf<Int, Int>()

    private var listener: Listener? = null
    private var lastIndex = 0

    init {
        for (item in items) {
            item.view.addListener(this)
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

        companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): Item {
                val viewJson = json.opt("view").optMap()
                return Item(
                    view = Thomas.model(viewJson),
                    identifier = identifierFromJson(json),
                    actions = json.opt("display_actions").optMap().map
                )
            }

            @Throws(JsonException::class)
            fun fromJsonList(json: JsonList): List<Item> =
                json.list.map { fromJson(it.optMap()) }
        }
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): PagerModel {
            val itemsJson = json.opt("items").optList()
            return PagerModel(
                items = Item.fromJsonList(itemsJson),
                isSwipeDisabled = json.opt("disable_swipe").getBoolean(false),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
        }
    }
}
