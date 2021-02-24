package com.urbanairship.debug.automation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.deferred.Deferred
import com.urbanairship.debug.databinding.UaItemScheduleDeferredBinding

class DeferredScheduleListAdapter(private val callback: ((schedule: Schedule<Deferred>) -> Unit)) : ListAdapter<Schedule<Deferred>, DeferredScheduleListAdapter.ViewHolder>(diffCallback) {

    class ViewHolder(val binding: UaItemScheduleDeferredBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.apply {
            with(holder.binding) {
                viewModel = DeferredScheduleListItem(this@apply)
                root.setOnClickListener {
                    this@DeferredScheduleListAdapter.callback(this@apply)
                }
                executePendingBindings()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = UaItemScheduleDeferredBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<Schedule<Deferred>>() {
            override fun areItemsTheSame(oldItem: Schedule<Deferred>, newItem: Schedule<Deferred>): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: Schedule<Deferred>, newItem: Schedule<Deferred>): Boolean {
                return oldItem == newItem
            }
        }
    }
}
