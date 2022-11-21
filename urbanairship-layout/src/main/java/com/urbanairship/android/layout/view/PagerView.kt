/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.PagerRecyclerView

internal class PagerView(
    context: Context,
    model: PagerModel,
    viewEnvironment: ViewEnvironment
) : FrameLayout(context), BaseView {

    fun interface OnScrollListener {
        fun onScrollTo(Position: Int, isInternalScroll: Boolean)
    }

    var scrollListener: OnScrollListener? = null

    private val view: PagerRecyclerView = PagerRecyclerView(context, model, viewEnvironment)

    private val modelListener = object : PagerModel.Listener {
        override fun scrollTo(position: Int) {
            if (position != NO_POSITION && position != view.displayedItemPosition) {
                view.scrollTo(position)
            }
        }

        override fun setVisibility(visible: Boolean) {
            this@PagerView.isGone = visible
        }
    }

    init {
        addView(view, MATCH_PARENT, MATCH_PARENT)
        LayoutUtils.applyBorderAndBackground(this, model)
        model.listener = modelListener

        view.setPagerScrollListener { position, isInternalScroll ->
            scrollListener?.onScrollTo(position, isInternalScroll)
        }

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _: View, insets: WindowInsetsCompat ->
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
    }
}
