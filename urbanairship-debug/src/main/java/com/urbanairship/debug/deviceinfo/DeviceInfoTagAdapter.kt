/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.urbanairship.debug.databinding.UaItemTagBinding

/**
 * RecyclerView adapter for a list of tags.
 */
class DeviceInfoTagAdapter : ListAdapter<String, DeviceInfoTagAdapter.ViewHolder>(TagFilterDiff()) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        val binding = UaItemTagBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        viewHolder.bind(getItem(i))
    }

    inner class ViewHolder internal constructor(private val binding: UaItemTagBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.getRoot()) {

        val tag: String?
            get() = binding.tag

        fun bind(tag: String) {
            binding.tag = tag
            binding.executePendingBindings()
        }

    }

    class TagFilterDiff : DiffUtil.ItemCallback<String>() {

        override fun areItemsTheSame(s: String, t1: String): Boolean {
            return s === t1
        }

        override fun areContentsTheSame(s: String, t1: String): Boolean {
            return s == t1
        }

    }
}
