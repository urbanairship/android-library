/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.PagerRecyclerView

internal class PagerView(
    context: Context,
    private val model: PagerModel,
    private val viewEnvironment: ViewEnvironment
) : FrameLayout(context), BaseView {

    private val view: PagerRecyclerView = PagerRecyclerView(context, model, viewEnvironment)

    private val modelListener = object : PagerModel.Listener {
        override fun onScrollToNext() {
            val position = view.displayedItemPosition
            val nextPosition = position + 1
            if (position != NO_POSITION && nextPosition < view.adapterItemCount) {
                view.scrollTo(nextPosition)
            }
        }

        override fun onScrollToPrevious() {
            val position = view.displayedItemPosition
            val previousPosition = position - 1
            if (position != NO_POSITION && previousPosition > -1) {
                view.scrollTo(previousPosition)
            }
        }
    }

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        addView(view, MATCH_PARENT, MATCH_PARENT)
        LayoutUtils.applyBorderAndBackground(this, model)
        model.setListener(modelListener)

        // Emit an init event so that we can connect to the indicator view, if one exists.
        model.onConfigured(view.displayedItemPosition, viewEnvironment.displayTimer().time)

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _: View, insets: WindowInsetsCompat ->
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
    }
}
