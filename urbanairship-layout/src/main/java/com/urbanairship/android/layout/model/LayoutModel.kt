/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.info.ViewGroupInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData

internal abstract class LayoutModel<VI : ViewGroupInfo>(
    viewType: ViewType,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : BaseModel(viewType, backgroundColor, border, environment) {

    /**
     * Implement in subclasses to return a list of [BaseModels][BaseModel] for items in the layout.
     *
     * @return a list of child `BaseModel` objects.
     */
    abstract val children: List<BaseModel>

    /**
     * {@inheritDoc}
     *
     * Overrides the default behavior in [BaseModel] to propagate the event by bubbling it up.
     */
    override fun onEvent(event: Event, layoutData: LayoutData): Boolean {
        return bubbleEvent(event, layoutData)
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the default behavior in [BaseModel] to propagate the event by trickling it
     * down to the children of this layout.
     */
    override fun trickleEvent(event: Event, layoutData: LayoutData): Boolean {
        for (child in children) {
            if (child.trickleEvent(event, layoutData)) {
                return true
            }
        }
        return false
    }
}
