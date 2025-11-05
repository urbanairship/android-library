/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.util.LayoutUtils

internal class PagerAdapter (
    private val pagerModel: PagerModel,
    private val viewEnvironment: ViewEnvironment
) : RecyclerView.Adapter<PagerAdapter.ViewHolder>() {

    private val items = mutableListOf<BaseModel<*, *, *>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.context)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = getItemAtPosition(position)
        holder.container.id = pagerModel.getPageViewId(position)
        holder.bind(model, viewEnvironment)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.onRecycled()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        return items[position].viewInfo.type.ordinal
    }

    fun getItemAtPosition(position: Int): BaseModel<*, *, *> {
        return items[position]
    }

    fun setItems(items: List<BaseModel<*, *, *>>) {
        if (this.items != items) {
            this.items.clear()
            this.items.addAll(items)
            notifyDataSetChanged()
        }
    }

    class ViewHolder private constructor(
        internal val container: ViewGroup
    ) : RecyclerView.ViewHolder(container) {

        constructor(context: Context) : this(FrameLayout(context))

        fun bind(item: BaseModel<*, *, *>, viewEnvironment: ViewEnvironment) {
            val view: View = item.createView(itemView.context, viewEnvironment, null)
            container.addView(
                view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Register a listener, so we can request insets when the view is attached.
            LayoutUtils.doOnAttachToWindow(itemView, Runnable { ViewCompat.requestApplyInsets(itemView) })
        }

        fun onRecycled() = container.removeAllViews()
    }
}
