/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.EventListener
import com.urbanairship.android.layout.event.EventSource
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import java.util.concurrent.CopyOnWriteArrayList

internal abstract class BaseModel(
    val viewType: ViewType,
    val backgroundColor: Color? = null,
    val border: Border? = null,
    val visibility: VisibilityInfo? = null,
    val eventHandlers: List<EventHandler>? = null,
    val enableBehaviors: List<EnableBehaviorType>? = null,
    protected val environment: ModelEnvironment
) : EventSource, EventListener {
    constructor(info: ViewInfo, environment: ModelEnvironment) : this(
        viewType = info.type,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment
    )

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
}
