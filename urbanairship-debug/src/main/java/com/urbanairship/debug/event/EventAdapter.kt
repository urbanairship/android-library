/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import com.urbanairship.debug.BR
import com.urbanairship.debug.databinding.UaItemEventBinding
import com.urbanairship.debug.event.persistence.EventEntity

/**
 * RecyclerView adapter for events.
 */
internal class EventAdapter(private val callback: ((event: EventEntity) -> Unit)) : PagedListAdapter<EventEntity, EventAdapter.ViewHolder>(diffCallback) {


    class ViewHolder(val binding: UaItemEventBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

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
        val binding = UaItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
