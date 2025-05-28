/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import androidx.core.view.isVisible
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.ScoreModel
import com.urbanairship.android.layout.property.ScoreStyle
import com.urbanairship.android.layout.property.ScoreStyle.NumberRange
import com.urbanairship.android.layout.property.ScoreStyle.WrappingNumberRange
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.ScoreItemView
import com.urbanairship.android.layout.widget.ShapeButton
import com.urbanairship.android.layout.widget.TappableView
import WrappingViewGroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Form input that presents a set of numeric options representing a score.
 */
internal class ScoreView(
    context: Context,
    model: ScoreModel,
) : ConstraintLayout(context), BaseView, TappableView {

    private val clicksChannel = Channel<Unit>(Channel.UNLIMITED)
    fun interface OnScoreSelectedListener {
        fun onScoreSelected(score: Int)
    }

    var scoreSelectedListener: OnScoreSelectedListener? = null

    private val scoreToViewIds = SparseIntArray()
    private var selectedScore: Int? = null
    private var isEnabled = true

    init {
        clipChildren = false
        val style = model.viewInfo.style
        when (style) {
            is NumberRange -> configureNumberRange(style)
            is WrappingNumberRange -> configureWrappingNumberRange(style)
        }

        model.contentDescription(context).ifNotEmpty { contentDescription = it }

        model.listener = object : ScoreModel.Listener {
            override fun onSetSelectedScore(value: Int?) {
                value?.let { setSelectedScore(it) }
            }

            override fun setEnabled(enabled: Boolean) {
                updateEnabledState(enabled)
            }

            override fun setVisibility(visible: Boolean) {
                this@ScoreView.isVisible = visible
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@ScoreView, old, new)
            }
        }
    }

    /** Configures the view to display the number range style. */
    private fun configureNumberRange(style: NumberRange) {
        val constraints = ConstraintSetBuilder.newBuilder(context)
        val bindings = style.bindings
        val start = style.start
        val end = style.end
        val viewIds = (start..end).map { i ->
            val viewId = View.generateViewId()
            val button = createShapeButton(i, bindings, viewId, constraints, 16, 16)
            scoreToViewIds.append(i, viewId)
            addView(button, LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT))
            viewId
        }.toIntArray()

        constraints
            .setHorizontalChainStyle(viewIds, androidx.constraintlayout.helper.widget.Flow.CHAIN_PACKED)
            .createHorizontalChainInParent(viewIds, 0, style.spacing)
            .build()
            .applyTo(this)
    }

    /** Configures the view to display the wrapping number range style. */
    private fun configureWrappingNumberRange(style: WrappingNumberRange) {
        val wrappingViewGroupId = View.generateViewId()
        val wrappingViewGroup = WrappingViewGroup(context).apply {
            id = wrappingViewGroupId
            itemSpacing = LayoutUtils.dpToPx(context, style.spacing)
            lineSpacing = LayoutUtils.dpToPx(context, style.wrapping.lineSpacing)
            maxItemsPerLine = style.wrapping.maxItemsPerLine
        }

        (style.start..style.end).forEach { i ->
            val button = ScoreItemView(
                context = context,
                label = i.toString(),
                bindings = style.bindings,
                padding = 0
            ).apply {
                setOnClickListener { onScoreClick(this, i) }
            }

            scoreToViewIds.append(i, button.id)
            wrappingViewGroup.addView(button)
        }

        addView(wrappingViewGroup)
    }


    private fun createShapeButton(
        score: Int,
        bindings: ScoreStyle.Bindings,
        viewId: Int,
        constraints: ConstraintSetBuilder,
        minHeight: Int,
        minWidth: Int
    ): ShapeButton {
        val button = ShapeButton(
            context,
            bindings.selected.shapes,
            bindings.unselected.shapes,
            score.toString(),
            bindings.selected.textAppearance,
            bindings.unselected.textAppearance
        ).apply {
            id = viewId
            setOnClickListener { onScoreClick(this, score) }
        }

        // Apply constraints to the button
        constraints.apply {
            squareAspectRatio(viewId)
            minWidth(viewId, minWidth)
            minHeight(viewId, minHeight)
        }

        return button
    }

    private fun updateCheckedState(view: View, selectedId: Int? = null) {
        if (view is Checkable) {
            view.isChecked = selectedId != null && view.id == selectedId
        }

        /// Only applies to wrapping style
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                updateCheckedState(view.getChildAt(i), selectedId)
            }
        }
    }

    fun setSelectedScore(score: Int?) {
        selectedScore = score

        // Check the selected view if there is one
        if (score != null) {
            val viewId = scoreToViewIds[score, -1]
            updateCheckedState(this, viewId)
        } else {
            /// No id is supplied uncheck all
            updateCheckedState(this);
        }
    }

    private fun onScoreClick(view: View, score: Int) {
        if (!isEnabled || score == selectedScore) return
        selectedScore = score
        setSelectedScore(score)
        // Notify our listener
        scoreSelectedListener?.onScoreSelected(score)
        // Emit click for tap handling
        clicksChannel.trySend(Unit)
    }

    private fun updateEnabledState(enabled: Boolean) {
        isEnabled = enabled
        for (i in 0 until childCount) {
            getChildAt(i).isEnabled = enabled
        }
    }

    override fun taps(): Flow<Unit> = clicksChannel.receiveAsFlow()
}
