/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.info.CheckableInfo

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

    val checkableViewId = View.generateViewId()

    fun setChecked(isChecked: Boolean) = listener?.setChecked(isChecked)

    fun setEnabled(isEnabled: Boolean) = listener?.setEnabled(isEnabled)
}
