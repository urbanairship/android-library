/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import androidx.annotation.CallSuper
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ToggleType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal abstract class CheckableModel(
    viewType: ViewType,
    val style: ToggleStyle,
    override val contentDescription: String?,
    backgroundColor: Color?,
    border: Border?
) : BaseModel(viewType, backgroundColor, border), Accessible {

    val checkableViewId = View.generateViewId()

    val toggleType: ToggleType = style.type

    abstract fun buildInputChangeEvent(isChecked: Boolean): Event
    abstract fun buildInitEvent(): Event

    private var listener: Listener? = null

    interface Listener {
        fun onSetChecked(isChecked: Boolean)
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun setChecked(isChecked: Boolean) {
        listener?.onSetChecked(isChecked)
    }

    @CallSuper
    fun onConfigured() {
        bubbleEvent(buildInitEvent(), LayoutData.empty())
    }

    fun onAttachedToWindow() {
        bubbleEvent(ViewAttachedToWindow(this), LayoutData.empty())
    }

    open fun onCheckedChange(isChecked: Boolean) {
        bubbleEvent(buildInputChangeEvent(isChecked), LayoutData.empty())
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        protected fun toggleStyleFromJson(json: JsonMap): ToggleStyle {
            val styleJson = json.opt("style").optMap()
            return ToggleStyle.fromJson(styleJson)
        }
    }
}
