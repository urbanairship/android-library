/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.text.TextUtils
import androidx.core.view.isGone
import com.google.android.material.button.MaterialButton
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.model.ButtonModel
import com.urbanairship.android.layout.model.LabelButtonModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.TappableView
import kotlinx.coroutines.flow.Flow

internal class LabelButtonView(
    context: Context,
    model: LabelButtonModel
) : MaterialButton(context, null, R.attr.borderlessButtonStyle), BaseView, TappableView {

    init {
        isAllCaps = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        minHeight = 0
        minimumHeight = 0
        insetTop = 0
        insetBottom = 0

        LayoutUtils.applyButtonModel(this, model)

        model.contentDescription.ifNotEmpty { contentDescription = it }

        model.listener = object : ButtonModel.Listener {
            override fun setEnabled(isEnabled: Boolean) {
                this@LabelButtonView.isEnabled = isEnabled
            }

            override fun setVisibility(visible: Boolean) {
                this@LabelButtonView.isGone = visible
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val autoHeight = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY
        val autoWidth = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY
        if (autoHeight || autoWidth) {
            val twelveDp = ResourceUtils.dpToPx(context, 12).toInt()
            val horizontal = if (autoWidth) twelveDp else 0
            val vertical = if (autoHeight) twelveDp else 0
            setPadding(horizontal, vertical, horizontal, vertical)
        } else {
            setPadding(0, 0, 0, 0)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun taps(): Flow<Unit> = debouncedClicks()
}
