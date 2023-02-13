/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import androidx.core.view.isGone
import com.urbanairship.android.layout.model.BaseModel
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
    model: EmptyModel
) : View(context), BaseView {

    init {
        LayoutUtils.applyBorderAndBackground(this, model)

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@EmptyView.isGone = visible
            }
        }
    }
}
