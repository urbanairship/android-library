/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.EventListener
import com.urbanairship.android.layout.event.EventSource
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.testing.OpenForTesting
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import java.util.concurrent.CopyOnWriteArrayList

@OpenForTesting
internal abstract class BaseModel(
    val type: ViewType,
    val backgroundColor: Color? = null,
    val border: Border? = null
) : EventSource, EventListener {

    val viewId: Int = View.generateViewId()

    private val listeners: MutableList<EventListener> = CopyOnWriteArrayList()

    //
    // EventSource impl
    //

    /**
     * {@inheritDoc}
     */
    override fun addListener(listener: EventListener) {
        listeners.add(listener)
    }

    /**
     * {@inheritDoc}
     */
    override fun removeListener(listener: EventListener) {
        listeners.remove(listener)
    }

    /**
     * Sets an event listener, removing any previously set listeners.
     */
    override fun setListener(listener: EventListener) {
        listeners.clear()
        listeners.add(listener)
    }

    //
    // EventListener impl
    //

    /**
     * {@inheritDoc}
     */
    override fun onEvent(event: Event, layoutData: LayoutData): Boolean {
        return false
    }

    //
    // Event propagation helpers
    //

    /**
     * Bubbles the given `Event` to any upstream listeners.
     *
     * @param event The `Event` to bubble up.
     * @param layoutData The `LayoutData` to bubble up.
     * @return `true` if the event was handled upstream, `false` otherwise.
     */
    protected fun bubbleEvent(event: Event, layoutData: LayoutData): Boolean {
        for (listener in listeners) {
            if (listener.onEvent(event, layoutData)) {
                return true
            }
        }
        return false
    }

    /**
     * Trickles the given `Event` to any downstream listeners.
     *
     * @param event The `Event` to trickle down.
     * @param layoutData The `LayoutData` to bubble up.
     * @return `true` if the event was handled downstream, `false` otherwise.
     */
    open fun trickleEvent(event: Event, layoutData: LayoutData): Boolean {
        return onEvent(event, layoutData)
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun backgroundColorFromJson(json: JsonMap): Color? {
            return Color.fromJsonField(json, "background_color")
        }

        @JvmStatic
        @Throws(JsonException::class)
        fun borderFromJson(json: JsonMap): Border? {
            val borderJson = json.opt("border").optMap()
            return if (borderJson.isEmpty) null else Border.fromJson(borderJson)
        }
    }
}
