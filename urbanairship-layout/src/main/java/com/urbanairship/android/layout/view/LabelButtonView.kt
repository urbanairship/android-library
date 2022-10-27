/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.text.TextUtils
import com.google.android.material.button.MaterialButton
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.ButtonModel
import com.urbanairship.android.layout.model.LabelButtonModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.ifNotEmpty

internal class LabelButtonView(
    context: Context,
    private val model: LabelButtonModel,
    viewEnvironment: ViewEnvironment
) : MaterialButton(context, null, R.attr.borderlessButtonStyle), BaseView {

    private val modelListener: ButtonModel.Listener = object : ButtonModel.Listener {
        override fun setEnabled(isEnabled: Boolean) {
            this@LabelButtonView.isEnabled = isEnabled
        }
    }

    init {
        id = model.viewId
        configure()
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

    private fun configure() {
        isAllCaps = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        LayoutUtils.applyButtonModel(this, model)
        model.setViewListener(modelListener)
        model.contentDescription.ifNotEmpty { contentDescription = it }

        minHeight = 0
        minimumHeight = 0
        insetTop = 0
        insetBottom = 0

        setOnClickListener { model.onClick() }
    }
}
