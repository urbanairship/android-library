package com.urbanairship.messagecenter.ui.widget

import android.graphics.Rect
import android.view.View
import androidx.annotation.Dimension
import androidx.recyclerview.widget.RecyclerView

internal class VerticalSpacingItemDecoration(
    @Dimension(Dimension.PX) private val spacingTop: Int,
    @Dimension(Dimension.PX) private val spacingMiddle: Int,
    @Dimension(Dimension.PX) private val spacingBottom: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) = with(outRect) {
        if (parent.getChildAdapterPosition(view) == 0) {
            top = spacingTop
        }

        bottom = if (parent.getChildAdapterPosition(view) == state.itemCount - 1) {
            spacingBottom
        } else {
            spacingMiddle
        }
    }
}
