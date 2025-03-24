package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.urbanairship.UALog
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.CustomViewModel
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.json.jsonMapOf

/** Wrapper for customer provided views. */
internal class CustomView(
    context: Context,
    model: CustomViewModel
) : FrameLayout(context), BaseView {

    init {
        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@CustomView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@CustomView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@CustomView, old, new)
            }
        }

        // Try to inflate the custom view using the registered handler
        val view = model.tryInflateView(context)
        if (view != null) {
            // If a handler is found and provided a view, add it to the layout
            addView(view)
        } else {
            // Otherwise, fallback to an empty View if no handler is found
            val fallbackView = View(context)
            addView(fallbackView)

            UALog.e { "No handler found for custom view: ${model.viewInfo.name}" }
        }
    }
}
