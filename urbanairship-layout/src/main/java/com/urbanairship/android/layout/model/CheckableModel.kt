/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import androidx.annotation.CallSuper
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ToggleType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData

internal abstract class CheckableModel(
    viewType: ViewType,
    val style: ToggleStyle,
    val toggleType: ToggleType,
    override val contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : BaseModel(viewType, backgroundColor, border, environment), Accessible {

    val checkableViewId = View.generateViewId()

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
}
