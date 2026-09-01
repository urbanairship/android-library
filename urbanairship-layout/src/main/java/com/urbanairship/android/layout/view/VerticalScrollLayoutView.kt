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
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.VerticalScrollLayoutModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.PercentBaseProvider

internal class VerticalScrollLayoutView(
    context: Context,
    model: VerticalScrollLayoutModel,
    viewEnvironment: ViewEnvironment
) : NestedScrollView(context), BaseView, PercentBaseProvider {

    private val contentView: View

    // A scene at the DSL floor predates the viewport: it was laid out with a scrolled percentage
    // falling back to its own content, and handing it a viewport now would give an empty `100%` a
    // screenful of blank its author never saw. So it's left with no base, same as before there was
    // one to offer.
    private val offersViewport = viewEnvironment.layoutVersion > Thomas.MIN_SUPPORTED_VERSION

    override val percentBaseWidth: Int = 0
    override var percentBaseHeight: Int = 0
        private set

    init {
        isFillViewport = false
        clipToOutline = true

        contentView = model.view.createView(context, viewEnvironment, null)
        addView(contentView, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // NestedScrollView measures its content with an UNSPECIFIED height so it can scroll, which
        // leaves percent-height descendants with nothing to resolve against. Record the viewport
        // for them to find. Taken from the incoming spec rather than measuredHeight, which is still
        // the previous pass's value while our content is being measured.
        //
        // Left at 0 for a scene at the DSL floor -- see `offersViewport`.
        if (offersViewport) {
            percentBaseHeight = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom)
                .coerceAtLeast(0)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
