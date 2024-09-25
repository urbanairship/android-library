/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.widget.NestedScrollView
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.ScrollLayoutModel
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.util.LayoutUtils

internal class ScrollLayoutView(
    context: Context,
    model: ScrollLayoutModel,
    viewEnvironment: ViewEnvironment
) : NestedScrollView(context), BaseView {

    init {
        isFillViewport = false
        clipToOutline = true

        LayoutUtils.applyBorderAndBackground(this, model)

        val contentView = model.view.createView(context, viewEnvironment, null).apply {
            layoutParams = if (model.direction == Direction.VERTICAL) {
                LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            } else {
                LayoutParams(WRAP_CONTENT, MATCH_PARENT)
            }
        }
        addView(contentView)

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@ScrollLayoutView.isGone = visible
            }
            override fun setEnabled(enabled: Boolean) {
                this@ScrollLayoutView.isEnabled = enabled
            }
        }

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _: View, insets: WindowInsetsCompat ->
            ViewCompat.dispatchApplyWindowInsets(contentView, insets)
        }
    }
}
