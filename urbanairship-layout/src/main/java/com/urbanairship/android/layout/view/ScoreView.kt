/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.util.SparseIntArray
import android.view.View
import android.widget.Checkable
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import com.urbanairship.R
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.ScoreModel
import com.urbanairship.android.layout.property.ScoreStyle.NumberRange
import com.urbanairship.android.layout.property.ScoreStyle.WrappingNumberRange
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.TappableView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Form input that presents a set of numeric options representing a score.
 * Accessibility is implemented by exposing the parent as a RadioGroup and children as RadioButtons.
 */
internal class ScoreView(
    context: Context,
    model: ScoreModel,
) : ConstraintLayout(context), BaseView, TappableView {

    private val clicksChannel = Channel<Unit>(Channel.UNLIMITED)
    var onScoreSelectedListener: ((Int) -> Unit)? = null

    private val scoreToViewIds = SparseIntArray()
    private var selectedScore: Int? = null
    private var isEnabled = true

    private val scoreBounds: Pair<Int, Int> = when (model.viewInfo.style) {
        is NumberRange -> model.viewInfo.style.start to model.viewInfo.style.end
        is WrappingNumberRange -> model.viewInfo.style.start to model.viewInfo.style.end
    }

    init {
        id = model.viewId
        clipChildren = false

        // Set the parent view to act as a RadioGroup for accessibility.
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = RadioGroup::class.java.name
                info.contentDescription = model.contentDescription(context)
            }
        })

        this.isFocusable = true
        this.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        when (model.viewInfo.style) {
            is NumberRange -> configureNumberRange(model.viewInfo.style)
            is WrappingNumberRange -> configureWrappingNumberRange(model.viewInfo.style)
        }

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

    private fun configureNumberRange(style: NumberRange) {
        val constraints = ConstraintSetBuilder.newBuilder(context)
        val viewIds = (style.start..style.end).map { i ->
            val button = ScoreItemView(
                context = context,
                label = i.toString(),
                bindings = style.bindings,
                padding = 0
            ).apply {
                setOnClickListener { onScoreClick(i) }
                makeViewAccessibleAsRadioButton(this, i)
            }
            scoreToViewIds.append(i, button.id)
            addView(button, LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT))
            button.id
        }.toIntArray()

        constraints
            .setHorizontalChainStyle(viewIds, androidx.constraintlayout.helper.widget.Flow.CHAIN_PACKED)
            .createHorizontalChainInParent(viewIds, 0, style.spacing)
            .build()
            .applyTo(this)
    }

    private fun configureWrappingNumberRange(style: WrappingNumberRange) {
        val wrappingViewGroupId = generateViewId()
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
                setOnClickListener { onScoreClick(i) }
                // Make each item a RadioButton for accessibility.
                makeViewAccessibleAsRadioButton(this, i)
            }
            scoreToViewIds.append(i, button.id)
            wrappingViewGroup.addView(button)
        }
        addView(wrappingViewGroup)
    }

    private fun makeViewAccessibleAsRadioButton(view: View, score: Int) {
        view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        view.isFocusable = true

        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = RadioButton::class.java.name
                info.isCheckable = true
                info.isClickable = true
                info.isChecked = score == selectedScore
                info.contentDescription = host.context.getString(
                    R.string.ua_score_selected_state_description,
                    score,
                    scoreBounds.second - scoreBounds.first + 1
                )
            }
        })
    }

    fun setSelectedScore(newScore: Int?) {
        val oldScore = selectedScore
        if (oldScore == newScore) return
        selectedScore = newScore

        oldScore?.let {
            findViewById<View>(scoreToViewIds.get(it, -1))?.let { view ->
                if (view is Checkable) view.isChecked = false
                // Invalidate accessibility info for the old item.
                view.postInvalidate()
            }
        }

        newScore?.let {
            findViewById<View>(scoreToViewIds.get(it, -1))?.let { view ->
                if (view is Checkable) view.isChecked = true
                // Invalidate accessibility info for the new item.
                view.postInvalidate()
            }
        }
    }

    private fun onScoreClick(score: Int) {
        if (!isEnabled || score == selectedScore) return
        setSelectedScore(score)
        onScoreSelectedListener?.invoke(score)
        clicksChannel.trySend(Unit)
    }

    private fun updateEnabledState(enabled: Boolean) {
        isEnabled = enabled
        for (i in 0 until scoreToViewIds.size()) {
            findViewById<View>(scoreToViewIds.valueAt(i))?.isEnabled = enabled
        }
    }

    override fun taps(): Flow<Unit> = clicksChannel.receiveAsFlow()
}
