/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.urbanairship.debug.databinding.UaItemEventFilterBinding
import com.urbanairship.debug.utils.SimpleDiffItemCallback

/**
 * RecyclerView adapter for event filters.
 */
internal class EventFilterAdapter : ListAdapter<EventFilter, EventFilterAdapter.ViewHolder>(SimpleDiffItemCallback()) {

    class ViewHolder(val binding: UaItemEventFilterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: EventFilterAdapter.ViewHolder, position: Int) {
        getItem(position)?.apply {
            with(holder.binding) {
                filter = this@apply
                executePendingBindings()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventFilterAdapter.ViewHolder {
        val binding = UaItemEventFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

}
