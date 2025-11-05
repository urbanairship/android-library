/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.Dimension
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout

public open class ClippableConstraintLayout @JvmOverloads public constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Clippable {

    private val clippableViewDelegate: ClippableViewDelegate = ClippableViewDelegate()

    /**
     * {@inheritDoc}
     */
    @MainThread
    override fun setClipPathBorderRadius(@Dimension borderRadius: Float) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius)
    }

    /**
     * {@inheritDoc}
     */
    @MainThread
    override fun setClipPathBorderRadius(borderRadii: FloatArray?) {
        clippableViewDelegate.setClipPathBorderRadii(this, borderRadii)
    }
}
