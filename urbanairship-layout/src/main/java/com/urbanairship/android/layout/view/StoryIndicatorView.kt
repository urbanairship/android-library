package com.urbanairship.android.layout.view

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.view.isGone
import com.urbanairship.android.layout.model.StoryIndicatorModel
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.StoryIndicatorSource
import com.urbanairship.android.layout.property.StoryIndicatorStyle
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator.INDICATOR_DIRECTION_START_TO_END

internal class StoryIndicatorView(
    context: Context,
    private val model: StoryIndicatorModel
) : LinearLayout(context), BaseView {

    private var progressIndicators: MutableList<ProgressBar> = ArrayList()
    private var lastProgress: Int = 0

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

            override fun onUpdate(size: Int, pageIndex: Int, progress: Int, durations: List<Int?>) {
                if (!isInitialized) {
                    isInitialized = true
                    setCount(size, durations)
                }

                val animated = progress > lastProgress
                lastProgress = progress
                setProgress(size, pageIndex, progress, animated)
            }

            override fun setVisibility(visible: Boolean) {
                this@StoryIndicatorView.isGone = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@StoryIndicatorView.isEnabled = enabled
            }
        }
    }

    fun setCount(count: Int, durations: List<Int?>) {
        when (val style = model.style) {
            is StoryIndicatorStyle.LinearProgress -> {
                val halfSpacing = ResourceUtils.dpToPx(context, style.spacing / 2).toInt()

                for (i in 0 until count) {
                    val progressIndicator = LinearProgressIndicator(context).apply {
                        id = model.getIndicatorViewId(i)
                        max = 100
                        setIndicatorColor(style.progressColor.resolve(context))
                        trackColor = style.trackColor.resolve(context)
                        indicatorDirection = INDICATOR_DIRECTION_START_TO_END
                        isIndeterminate = false
                    }

                    val lp = LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply {
                        marginStart = if (i == 0) 0 else halfSpacing
                        marginEnd = if (i == count - 1) 0 else halfSpacing
                    }

                    when (style.sizing) {
                        StoryIndicatorStyle.LinearProgress.SizingType.EQUAL, null -> {
                            lp.apply {
                                weight = 1F
                            }
                        }
                        StoryIndicatorStyle.LinearProgress.SizingType.PAGE_DURATION -> {
                            lp.apply {
                                // If we don't have any automated action and so no delay
                                // Set a static 10 width to have a small static indicator for the page
                                durations[i]?.let {
                                    weight = it.toFloat()
                                } ?: run {
                                    progressIndicator.visibility = View.GONE
                                }
                            }
                        }
                    }

                    addView(progressIndicator, lp)
                    progressIndicators.add(progressIndicator)
                }
            }
        }
    }

    fun setProgress(count: Int, pageIndex: Int, progress: Int, animated: Boolean) {
        if (progressIndicators.isEmpty() || progressIndicators.size <= pageIndex) {
            return
        }
        val storyHeight = this.height
        for (i in 0 until count) {
            (progressIndicators[i] as? LinearProgressIndicator)?.let {
                if (i == pageIndex) {
                    if (model.source == StoryIndicatorSource.CURRENT_PAGE) {
                        it.visibility = View.VISIBLE
                    }
                    it.trackThickness = (storyHeight * 0.8).toInt()
                    it.setProgressCompat(progress, animated)
                } else {
                    if (model.source == StoryIndicatorSource.CURRENT_PAGE) {
                        it.visibility = View.GONE
                    }
                    if (i > pageIndex) {
                        it.trackThickness = (storyHeight * 0.3).toInt()
                        it.setProgressCompat(0, false)
                    } else {
                        it.trackThickness = (storyHeight * 0.3).toInt()
                        it.setProgressCompat(100, false)
                    }
                }
            }
        }
    }
}
