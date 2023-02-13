/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.util.SparseIntArray
import android.view.View
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isGone
import com.urbanairship.android.layout.model.ScoreModel
import com.urbanairship.android.layout.property.ScoreStyle.NumberRange
import com.urbanairship.android.layout.property.ScoreType
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.ShapeButton
import com.urbanairship.android.layout.widget.TappableView
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

    init {
        LayoutUtils.applyBorderAndBackground(this, model)
        val constraints = ConstraintSetBuilder.newBuilder(context)
        val style = model.style
        when (style.type) {
            ScoreType.NUMBER_RANGE -> configureNumberRange(style as NumberRange, constraints)
        }
        constraints.build().applyTo(this)

        model.contentDescription.ifNotEmpty { contentDescription = it }

        model.listener = object : ScoreModel.Listener {
            override fun onSetSelectedScore(value: Int?) {
                value?.let { setSelectedScore(it) }
            }

            override fun setVisibility(visible: Boolean) {
                this@ScoreView.isGone = visible
            }
        }
    }

    private fun configureNumberRange(style: NumberRange, constraints: ConstraintSetBuilder) {
        val bindings = style.bindings
        val start = style.start
        val end = style.end
        val viewIds = IntArray(end - start + 1)
        for (i in start..end) {
            val button: ShapeButton = object : ShapeButton(
                context,
                bindings.selected.shapes,
                bindings.unselected.shapes,
                i.toString(),
                bindings.selected.textAppearance,
                bindings.unselected.textAppearance
            ) {
                // No-op. Checked state is updated by the click listener.
                override fun toggle() = Unit
            }

            val viewId = View.generateViewId()
            button.id = viewId
            viewIds[i - start] = viewId
            scoreToViewIds.append(i, viewId)
            button.setOnClickListener { v: View -> onScoreClick(v, i) }
            constraints.squareAspectRatio(viewId)
            constraints.minHeight(viewId, 16)
            addView(button, LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT))
        }
        constraints.setHorizontalChainStyle(viewIds, ConstraintSet.CHAIN_PACKED)
            .createHorizontalChainInParent(viewIds, 0, style.spacing)
    }

    fun setSelectedScore(score: Int?) {
        selectedScore = score
        if (score != null) {
            // Check the selected view
            val viewId = scoreToViewIds[score, -1]
            if (viewId > -1) {
                val view = findViewById<View>(viewId)
                (view as? Checkable)?.isChecked = true
            }
        } else {
            // Uncheck all items
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                (child as? Checkable)?.isChecked = false
            }
        }
    }

    private fun onScoreClick(view: View, score: Int) {
        if (score == selectedScore) return
        selectedScore = score

        // Uncheck other items in the view
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            (child as? Checkable)?.isChecked = view.id == child.id
        }

        // Notify our listener
        scoreSelectedListener?.onScoreSelected(score)
        // Emit click for tap handling
        clicksChannel.trySend(Unit)
    }

    override fun taps(): Flow<Unit> = clicksChannel.receiveAsFlow()
}
