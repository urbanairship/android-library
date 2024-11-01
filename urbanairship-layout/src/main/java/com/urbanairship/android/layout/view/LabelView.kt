/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.util.resolveContentDescription

internal class LabelView(
    context: Context,
    model: LabelModel
) : AppCompatTextView(context), BaseView {

    init {
        LayoutUtils.applyLabelModel(this, model)
        LayoutUtils.applyBorderAndBackground(this, model)

        context.resolveContentDescription(model.contentDescription, model.localizedContentDescription)?.ifNotEmpty {
            contentDescription = it
        }

        if (model.accessibilityHidden) {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        ViewCompat.setAccessibilityHeading(this, model.accessibilityRole?.type == LabelInfo.AccessibilityRoleType.HEADING)

        isClickable = false


        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@LabelView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@LabelView.isEnabled = enabled
            }
        }
    }
}
