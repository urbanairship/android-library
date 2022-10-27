/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.EmptyModel
import com.urbanairship.android.layout.util.LayoutUtils

/**
 * An empty view that can have a background and border.
 *
 * Useful for the nub on a banner or as dividers between items.
 *
 * @see EmptyModel
 */
internal class EmptyView(
    context: Context,
    private val model: EmptyModel,
    viewEnvironment: ViewEnvironment
) : View(context), BaseView {

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        LayoutUtils.applyBorderAndBackground(this, model)
    }
}
