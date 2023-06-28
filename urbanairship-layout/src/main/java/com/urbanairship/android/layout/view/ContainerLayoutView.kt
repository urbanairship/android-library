/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.animation.LayoutTransition
import android.content.Context
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener as OnApplyWindowInsetsListenerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.ContainerLayoutModel
import com.urbanairship.android.layout.model.ContainerLayoutModel.Item
import com.urbanairship.android.layout.property.Margin
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.ClippableConstraintLayout

internal class ContainerLayoutView(
    context: Context,
    model: ContainerLayoutModel,
    private val viewEnvironment: ViewEnvironment
) : ClippableConstraintLayout(context), BaseView {

    private val frameShouldIgnoreSafeArea = SparseBooleanArray()
    private val frameMargins = SparseArray<Margin>()

    init {
        clipChildren = true
        val constraintBuilder = ConstraintSetBuilder.newBuilder(context)
        addItems(model.items, constraintBuilder)
        LayoutUtils.applyBorderAndBackground(this, model)
        constraintBuilder.build().applyTo(this)
        ViewCompat.setOnApplyWindowInsetsListener(this, WindowInsetsListener(constraintBuilder))

        layoutTransition = LayoutTransition().apply {
            // Prevent unwanted flickering when switching visibility between two views
            disableTransitionType(LayoutTransition.APPEARING)
        }

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@ContainerLayoutView.isGone = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@ContainerLayoutView.isEnabled = enabled
            }
        }
    }

    private fun addItems(items: List<Item>, constraintBuilder: ConstraintSetBuilder) {
        for (item in items) {
            addItem(constraintBuilder, item)
        }
    }

    private fun addItem(constraintBuilder: ConstraintSetBuilder, item: Item) {
        val itemView = item.model.createView(context, viewEnvironment)

        val frameId = View.generateViewId()
        val frame: ViewGroup = FrameLayout(context).apply {
            id = frameId
            addView(itemView, MATCH_PARENT, MATCH_PARENT)
        }

        addView(frame)

        val info = item.info
        constraintBuilder
            .position(info.position, frameId)
            .size(info.size, frameId)
            .margin(info.margin, frameId)

        frameShouldIgnoreSafeArea.put(frameId, info.ignoreSafeArea)
        frameMargins.put(frameId, info.margin ?: Margin.NONE)
    }

    private inner class WindowInsetsListener(
        private val constraintBuilder: ConstraintSetBuilder
    ) : OnApplyWindowInsetsListenerCompat {
        override fun onApplyWindowInsets(
            v: View,
            windowInsets: WindowInsetsCompat
        ): WindowInsetsCompat {
            val applied = ViewCompat.onApplyWindowInsets(v, windowInsets)
            val insets = applied.getInsets(WindowInsetsCompat.Type.systemBars())
            if (applied.isConsumed || insets == Insets.NONE) {
                return WindowInsetsCompat.CONSUMED
            }

            var constraintsChanged = false
            for (i in 0 until childCount) {
                val child = getChildAt(i) as ViewGroup
                val shouldIgnoreSafeArea = frameShouldIgnoreSafeArea[child.id, false]
                if (shouldIgnoreSafeArea) {
                    ViewCompat.dispatchApplyWindowInsets(child, applied)
                } else {
                    ViewCompat.dispatchApplyWindowInsets(child, applied.inset(insets))
                    // Handle insets by adding onto the child frame's margins.
                    val margin = frameMargins[child.id]
                    constraintBuilder.margin(margin, insets, child.id)
                    constraintsChanged = true
                }
            }
            if (constraintsChanged) {
                constraintBuilder.build().applyTo(this@ContainerLayoutView)
            }
            return applied.inset(insets)
        }
    }
}
