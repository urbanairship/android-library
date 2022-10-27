/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.util.SparseIntArray
import android.view.View
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import androidx.constraintlayout.widget.ConstraintSet
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.ScoreModel
import com.urbanairship.android.layout.property.ScoreStyle.NumberRange
import com.urbanairship.android.layout.property.ScoreType
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.ShapeButton

/**
 * Form input that presents a set of numeric options representing a score.
 */
internal class ScoreView(
    context: Context,
    private val model: ScoreModel,
    viewEnvironment: ViewEnvironment
) : ConstraintLayout(context), BaseView {

    private var selectedScore: Int? = null
    private val scoreToViewIds = SparseIntArray()

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        LayoutUtils.applyBorderAndBackground(this, model)
        val constraints = ConstraintSetBuilder.newBuilder(context)
        val style = model.style
        when (style.type) {
            ScoreType.NUMBER_RANGE -> configureNumberRange(style as NumberRange, constraints)
        }
        constraints.build().applyTo(this)

        model.contentDescription.ifNotEmpty { contentDescription = it }

        // Restore state from the model, if we have any.
        model.selectedScore?.let { setSelectedScore(it) }

        model.onConfigured()

        LayoutUtils.doOnAttachToWindow(this) { model.onAttachedToWindow() }
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

    private fun setSelectedScore(score: Int) {
        selectedScore = score
        val viewId = scoreToViewIds[score, -1]
        if (viewId > -1) {
            val view = findViewById<View>(viewId)
            (view as? Checkable)?.isChecked = true
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

        // Notify our model
        model.onScoreChange(score)
    }
}
