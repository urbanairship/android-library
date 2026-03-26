package com.urbanairship.android.layout.view

import android.content.Context
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.urbanairship.android.layout.model.AsyncLayoutModel
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.util.LayoutUtils
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Container for asynchronously fetched layout content. */
internal class AsyncLayoutView(
    context: Context,
    model: AsyncLayoutModel
) : FrameLayout(context), BaseView {

    init {
        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@AsyncLayoutView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@AsyncLayoutView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@AsyncLayoutView, old, new)
            }
        }

        model.state
            .onEach(::render)
            .launchIn(model.viewScope)
    }

    fun render(
        content: AsyncLayoutModel.ContentToDisplay
    ) {
        val view = with(content) {
            model.createView(
                context = context,
                viewEnvironment = viewEnvironment,
                itemProperties = itemProperties
            )
        }

        removeAllViews()
        addView(view)
    }
}
