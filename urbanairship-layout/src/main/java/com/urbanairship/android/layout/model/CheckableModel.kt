/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.info.CheckableInfo
import com.urbanairship.android.layout.info.Identifiable
import com.urbanairship.android.layout.info.RecentlyIdentifiable

internal abstract class CheckableModel<T : View, I : CheckableInfo>(
    viewInfo: I,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<T, I, CheckableModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    interface Listener : BaseModel.Listener {

        fun setChecked(checked: Boolean)
    }

    // We need to use the resolved id for the checkableViewId to make labelFor work since
    // a checkable view is a frame layout that wraps a checkbox/radio/switch.
    override val viewId: Int = environment.viewIdResolver.viewId()
    val checkableViewId = with(environment.viewIdResolver) {
        when (viewInfo) {
            is RecentlyIdentifiable -> viewId(viewInfo.identifier, viewInfo.type)
            is Identifiable -> viewId(viewInfo.identifier, viewInfo.type)
            else -> viewId()
        }
    }

    fun setChecked(isChecked: Boolean) = listener?.setChecked(isChecked)

    fun setEnabled(isEnabled: Boolean) = listener?.setEnabled(isEnabled)
}
