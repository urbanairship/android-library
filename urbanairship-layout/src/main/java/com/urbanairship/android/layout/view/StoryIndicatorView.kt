package com.urbanairship.android.layout.view

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.view.isGone
import com.urbanairship.android.layout.model.StoryIndicatorModel
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.StoryIndicatorStyle
import com.urbanairship.android.layout.util.LayoutUtils

internal class StoryIndicatorView(
    context: Context,
    private val model: StoryIndicatorModel
) : LinearLayout(context), BaseView {

    init {
        when (val style = model.style) {
            is StoryIndicatorStyle.LinearProgress -> {
                orientation = if (style.direction == Direction.VERTICAL) VERTICAL else HORIZONTAL
                gravity = Gravity.CENTER
            }
        }

        LayoutUtils.applyBorderAndBackground(this, model)

        model.listener = object : StoryIndicatorModel.Listener {
            private var isInitialized = false

            override fun onUpdate(size: Int, progress: Int) {
                if (!isInitialized) {
                    isInitialized = true
                    setCount(size)
                }
                setProgress(progress)
            }

            override fun setVisibility(visible: Boolean) {
                this@StoryIndicatorView.isGone = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@StoryIndicatorView.isEnabled = enabled
            }
        }
    }

    fun setCount(count: Int) {
        // TODO(stories): Create the indicator using the number of sections given by 'count'.
    }

    fun setProgress(progress: Int) {
        // TODO(stories): Update the progress of the indicator. I think we should be able to use a
        //  single progress value for both sources (pager, current_page), but we may need to add
        //  or modify params to make that work.
    }
}
