/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.urbanairship.debug.databinding.UaItemDebugScreenBinding
import com.urbanairship.debug.utils.SimpleDiffItemCallback

/**
 * RecyclerView adapter for debug view listing.
 */
internal class DebugEntryAdapter(private val callback: ((screen: DebugEntry) -> Unit))
    : ListAdapter<DebugEntry, DebugEntryAdapter.ViewHolder>(SimpleDiffItemCallback()) {

    class ViewHolder(val binding: UaItemDebugScreenBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: DebugEntryAdapter.ViewHolder, position: Int) {
        getItem(position)?.apply {
            with(holder.binding) {
                screen = this@apply
                root.setOnClickListener {
                    this@DebugEntryAdapter.callback(this@apply)
                }
                executePendingBindings()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebugEntryAdapter.ViewHolder {
        val binding = UaItemDebugScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
}
