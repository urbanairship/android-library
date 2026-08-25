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
import com.urbanairship.android.layout.widget.PercentBaseProvider

internal class HorizontalScrollLayoutView(
    context: Context,
    model: HorizontalScrollLayoutModel,
    viewEnvironment: ViewEnvironment
) : HorizontalScrollView(context), BaseView, PercentBaseProvider {

    private val contentView: View

    override var percentBaseWidth: Int = 0
        private set
    override val percentBaseHeight: Int = 0

    init {
        isFillViewport = false
        clipToOutline = true

        contentView = model.view.createView(context, viewEnvironment, null)
        addView(contentView, LayoutParams(WRAP_CONTENT, MATCH_PARENT))

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // HorizontalScrollView measures its content with an UNSPECIFIED width so it can scroll,
        // which leaves percent-width descendants with nothing to resolve against. Record the
        // viewport for them to find. Taken from the incoming spec rather than measuredWidth, which
        // is still the previous pass's value while our content is being measured.
        percentBaseWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
            .coerceAtLeast(0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
