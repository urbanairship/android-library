/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.info.AccessibleRoleInfoType
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ifNotEmpty

internal class LabelView(
    context: Context,
    model: LabelModel
) : AppCompatTextView(context), BaseView {

    init {
        LayoutUtils.applyLabelModel(this, model)
        LayoutUtils.applyBorderAndBackground(this, model)

        model.contentDescription.ifNotEmpty { contentDescription = it }

        isClickable = false
        ViewCompat.setAccessibilityHeading(this, model.roleInfo?.type == AccessibleRoleInfoType.HEADING)

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
