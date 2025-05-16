/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.VerticalScrollLayoutModel
import com.urbanairship.android.layout.util.LayoutUtils

internal class VerticalScrollLayoutView(
    context: Context,
    model: VerticalScrollLayoutModel,
    viewEnvironment: ViewEnvironment
) : NestedScrollView(context), BaseView {

    init {
        isFillViewport = false
        clipToOutline = true

        val contentView = model.view.createView(context, viewEnvironment, null).apply {
            LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        addView(contentView)

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@VerticalScrollLayoutView.isVisible = visible
            }
            override fun setEnabled(enabled: Boolean) {
                this@VerticalScrollLayoutView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@VerticalScrollLayoutView, old, new)
            }
        }

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _: View, insets: WindowInsetsCompat ->
            ViewCompat.dispatchApplyWindowInsets(contentView, insets)
        }
    }
}
