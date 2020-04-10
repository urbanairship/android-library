/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.customevent

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.debug.databinding.UaItemPropertyBinding
import com.urbanairship.json.JsonValue

/**
 * RecyclerView adapter for properties
 */
class PropertyAdapter(private val callback: ((property: Pair<String, JsonValue>) -> Unit)) : ListAdapter<Pair<String, JsonValue>, PropertyAdapter.ViewHolder>(PropertyFilterDiff()) {

    class ViewHolder(val binding: UaItemPropertyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        val binding = UaItemPropertyBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        getItem(i)?.let { propertyPair ->
            with(viewHolder.binding) {
                this.name = propertyPair.first

                root.setOnClickListener {
                    callback(propertyPair)
                }
                executePendingBindings()
            }
        }
    }

    class PropertyFilterDiff : DiffUtil.ItemCallback<Pair<String, JsonValue>>() {

        override fun areItemsTheSame(s: Pair<String, JsonValue>, t1: Pair<String, JsonValue>): Boolean {
            return s === t1
        }

        override fun areContentsTheSame(s: Pair<String, JsonValue>, t1: Pair<String, JsonValue>): Boolean {
            return s == t1
        }
    }
}
