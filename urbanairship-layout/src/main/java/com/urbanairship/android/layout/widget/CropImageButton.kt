package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import com.urbanairship.android.layout.property.Position

/** Base `AppCompatImageButton` with support for "fit_crop" and image positioning. */
internal class CropImageButton(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    private val delegate = CropImageDelegate(this)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        delegate.onSizeChanged()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        delegate.setImageDrawable()
    }

    fun setParentLayoutParams(layoutParams: ViewGroup.LayoutParams?) {
        delegate.setParentLayoutParams(layoutParams)
    }

    fun setImagePosition(position: Position?) {
        delegate.setImagePosition(position)
    }
}