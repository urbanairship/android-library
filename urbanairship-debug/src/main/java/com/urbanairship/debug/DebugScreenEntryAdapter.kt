/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.urbanairship.debug.databinding.ItemDebugScreenBinding
import com.urbanairship.debug.utils.SimpleDiffItemCallback

/**
 * RecyclerView adapter for debug view listing.
 */
internal class DebugScreenEntryAdapter(private val callback: ((screen: DebugScreenEntry) -> Unit))
    : ListAdapter<DebugScreenEntry, DebugScreenEntryAdapter.ViewHolder>(SimpleDiffItemCallback()) {

    class ViewHolder(val binding: ItemDebugScreenBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: DebugScreenEntryAdapter.ViewHolder, position: Int) {
        getItem(position)?.apply {
            with(holder.binding) {
                screen = this@apply
                root.setOnClickListener {
                    this@DebugScreenEntryAdapter.callback(this@apply)
                }
                executePendingBindings()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebugScreenEntryAdapter.ViewHolder {
        var binding = ItemDebugScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
}
