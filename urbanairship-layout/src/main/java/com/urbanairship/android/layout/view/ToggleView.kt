/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.ToggleModel
import com.urbanairship.android.layout.widget.CheckableView

/**
 * Checkbox or Switch view for use within a `FormController` or `NpsController`.
 */
internal class ToggleView(
    context: Context,
    model: ToggleModel,
    viewEnvironment: ViewEnvironment
) : CheckableView<ToggleModel>(context, model, viewEnvironment) {

    override fun configure() {
        super.configure()

        checkableView.setOnCheckedChangeListener(checkedChangeListener)
    }
}
