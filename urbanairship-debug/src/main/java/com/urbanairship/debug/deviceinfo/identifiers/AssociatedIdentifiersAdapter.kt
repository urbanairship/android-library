/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.identifiers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.debug.databinding.UaItemAssociatedIdentifierBinding
import com.urbanairship.debug.utils.SimpleDiffItemCallback

/**
 * RecyclerView adapter for a associated identifiers.
 */
class AssociatedIdentifiersAdapter : ListAdapter<AssociatedIdentifier, AssociatedIdentifiersAdapter.ViewHolder>(SimpleDiffItemCallback<AssociatedIdentifier>()) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        val binding = UaItemAssociatedIdentifierBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        viewHolder.bind(getItem(i))
    }

    inner class ViewHolder internal constructor(private val binding: UaItemAssociatedIdentifierBinding) : RecyclerView.ViewHolder(binding.root) {

        val key: String?
            get() = binding.key

        val value: String?
            get() = binding.value

        fun bind(identifier: AssociatedIdentifier) {
            binding.key = identifier.key
            binding.value = identifier.value
            binding.executePendingBindings()
        }
    }
}
