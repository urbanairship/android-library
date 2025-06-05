package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.IconModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ifNotEmpty

internal class IconView(
    context: Context,
    private val model: IconModel,
): AppCompatImageView(context), BaseView {

    init {
        model.contentDescription(context).ifNotEmpty { contentDescription = it }

        if (model.viewInfo.accessibilityHidden == true) {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        isClickable = false
        adjustViewBounds = true

        model.listener = object : BaseModel.Listener {
            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@IconView, old, new)
            }

            override fun setVisibility(visible: Boolean) {
                this@IconView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@IconView.isEnabled = enabled
            }

            override fun onStateUpdated(state: ThomasState) {
                updateIcon(state)
            }
        }
    }

    private fun updateIcon(state: ThomasState) {
        val resolved = state.resolveOptional(
            overrides = model.viewInfo.viewOverrides?.icon,
            default = model.viewInfo.image
        )
            ?.getDrawable(context, isEnabled)
            ?: return

        setImageDrawable(resolved)
    }
}
