/* Copyright Airship and Contributors */

package com.urbanairship.debug.automation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.urbanairship.debug.databinding.UaItemAutomationDetailsBinding
import com.urbanairship.debug.databinding.UaItemAutomationDetailsClickableBinding
import com.urbanairship.debug.utils.SimpleDiffItemCallback

class AutomationDetailsAdapter : ListAdapter<AutomationDetail, ViewHolder>(SimpleDiffItemCallback<AutomationDetail>()) {
    internal class BasicViewHolder(val binding: UaItemAutomationDetailsBinding) : RecyclerView.ViewHolder(binding.root)
    internal class ClickableViewHolder(val binding: UaItemAutomationDetailsClickableBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val TEXT_ROW_TYPE = 0
        private const val CLICKABLE_ROW_TYPE = 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == CLICKABLE_ROW_TYPE) {
            val binding = UaItemAutomationDetailsClickableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ClickableViewHolder(binding)
        } else {
            val binding = UaItemAutomationDetailsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            BasicViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder.itemViewType == CLICKABLE_ROW_TYPE) {
            val clickableViewHolder = holder as ClickableViewHolder
            getItem(position).let { detail ->
                with(clickableViewHolder.binding) {
                    viewModel = detail
                    root.setOnClickListener {
                        detail.callback!!()
                    }
                    executePendingBindings()
                }
            }
        } else {
            val basicViewHolder = holder as BasicViewHolder
            getItem(position)?.let { detail ->
                with(basicViewHolder.binding) {
                    viewModel = detail
                    executePendingBindings()
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).callback != null) {
            CLICKABLE_ROW_TYPE
        } else {
            TEXT_ROW_TYPE
        }
    }
}
