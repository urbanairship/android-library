/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.event

import android.arch.paging.PagedListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.urbanairship.debug.BR
import com.urbanairship.debug.databinding.UaItemEventBinding
import com.urbanairship.debug.event.persistence.EventEntity

/**
 * RecyclerView adapter for events.
 */
internal class EventAdapter(private val callback: ((event: EventEntity) -> Unit)) : PagedListAdapter<EventEntity, EventAdapter.ViewHolder>(diffCallback) {


    class ViewHolder(val binding: UaItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.apply {
            with(holder.binding) {
                setVariable(BR.viewModel, EventItem(this@apply))
                root.setOnClickListener {
                    this@EventAdapter.callback(this@apply)
                }
                executePendingBindings()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        var binding = UaItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<EventEntity>() {
            override fun areItemsTheSame(oldItem: EventEntity, newItem: EventEntity): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: EventEntity, newItem: EventEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
