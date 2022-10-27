/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import androidx.appcompat.widget.AppCompatTextView
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ifNotEmpty

internal class LabelView(
    context: Context,
    private val model: LabelModel,
    viewEnvironment: ViewEnvironment
) : AppCompatTextView(context), BaseView {

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        LayoutUtils.applyLabelModel(this, model)
        LayoutUtils.applyBorderAndBackground(this, model)
        model.contentDescription.ifNotEmpty { contentDescription = it }
    }
}
