package com.urbanairship.debug.automation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.automation.Schedule
import com.urbanairship.automation.actions.Actions
import com.urbanairship.debug.databinding.UaItemScheduleActionsBinding
import com.urbanairship.iam.InAppMessage

internal class ActionsScheduleListAdapter(private val callback: ((schedule: Schedule<Actions>) -> Unit)) : ListAdapter<Schedule<Actions>, ActionsScheduleListAdapter.ViewHolder>(diffCallback) {

    class ViewHolder(val binding: UaItemScheduleActionsBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.apply {
            with(holder.binding) {
                viewModel = ActionsScheduleListItem(this@apply)
                root.setOnClickListener {
                    this@ActionsScheduleListAdapter.callback(this@apply)
                }
                executePendingBindings()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = UaItemScheduleActionsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<Schedule<Actions>>() {
            override fun areItemsTheSame(oldItem: Schedule<Actions>, newItem: Schedule<Actions>): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: Schedule<Actions>, newItem: Schedule<Actions>): Boolean {
                return oldItem == newItem
            }
        }
    }
}