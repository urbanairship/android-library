/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.HorizontalScrollLayoutModel
import com.urbanairship.android.layout.util.LayoutUtils

internal class HorizontalScrollLayoutView(
    context: Context,
    model: HorizontalScrollLayoutModel,
    viewEnvironment: ViewEnvironment
) : HorizontalScrollView(context), BaseView {

    init {
        isFillViewport = false
        clipToOutline = true

        val contentView = model.view.createView(context, viewEnvironment, null).apply {
            LayoutParams(WRAP_CONTENT, MATCH_PARENT)
        }
        addView(contentView)

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@HorizontalScrollLayoutView.isVisible = visible
            }
            override fun setEnabled(enabled: Boolean) {
                this@HorizontalScrollLayoutView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@HorizontalScrollLayoutView, old, new)
            }
        }

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _: View, insets: WindowInsetsCompat ->
            ViewCompat.dispatchApplyWindowInsets(contentView, insets)
        }
    }
}
