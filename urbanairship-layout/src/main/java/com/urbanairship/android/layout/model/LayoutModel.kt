/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal abstract class LayoutModel(
    viewType: ViewType,
    backgroundColor: Color? = null,
    border: Border? = null
) : BaseModel(viewType, backgroundColor, border) {

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

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): LayoutModel {
            val typeString = json.opt("type").optString()
            return when (ViewType.from(typeString)) {
                ViewType.CONTAINER -> ContainerLayoutModel.fromJson(json)
                ViewType.LINEAR_LAYOUT -> LinearLayoutModel.fromJson(json)
                ViewType.SCROLL_LAYOUT -> ScrollLayoutModel.fromJson(json)
                ViewType.PAGER_CONTROLLER -> PagerController.fromJson(json)
                ViewType.FORM_CONTROLLER -> FormController.fromJson(json)
                ViewType.NPS_FORM_CONTROLLER -> NpsFormController.fromJson(json)
                ViewType.CHECKBOX_CONTROLLER -> CheckboxController.fromJson(json)
                ViewType.RADIO_INPUT_CONTROLLER -> RadioInputController.fromJson(json)
                else -> throw JsonException(
                    "Error inflating layout! Unrecognized view type: $typeString"
                )
            }
        }
    }
}
