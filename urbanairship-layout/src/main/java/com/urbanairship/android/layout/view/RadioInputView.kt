/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.RadioInputModel
import com.urbanairship.android.layout.widget.CheckableView

internal class RadioInputView(
    context: Context,
    model: RadioInputModel,
    viewEnvironment: ViewEnvironment
) : CheckableView<RadioInputModel>(context, model, viewEnvironment) {

    override fun configure() {
        super.configure()

        model.setListener { isChecked: Boolean -> setCheckedInternal(isChecked) }

        checkableView.setOnCheckedChangeListener(checkedChangeListener)
    }
}
