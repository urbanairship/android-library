/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.Dimension
import androidx.annotation.MainThread

public class ClippableFrameLayout @JvmOverloads public constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes), Clippable {

    private val clippableViewDelegate = ClippableViewDelegate()

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
