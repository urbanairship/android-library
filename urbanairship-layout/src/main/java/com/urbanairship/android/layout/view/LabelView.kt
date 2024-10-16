/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import com.urbanairship.android.layout.info.AccessibleRoleInfo
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

        ViewCompat.setAccessibilityHeading(this, model.roleInfo == AccessibleRoleInfo.HEADING);

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@LabelView.isGone = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@LabelView.isEnabled = enabled
            }
        }
    }
}
