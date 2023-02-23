/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ToggleType
import com.urbanairship.android.layout.property.ViewType

internal abstract class CheckableModel<T : View>(
    viewType: ViewType,
    val style: ToggleStyle,
    val toggleType: ToggleType,
    val contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : BaseModel<T, CheckableModel.Listener>(
    viewType = viewType,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {

    interface Listener : BaseModel.Listener {
        fun setChecked(checked: Boolean)
    }

    val checkableViewId = View.generateViewId()

    fun setChecked(isChecked: Boolean) = listener?.setChecked(isChecked)

    fun setEnabled(isEnabled: Boolean) = listener?.setEnabled(isEnabled)
}
