package com.urbanairship.debug.contact.open

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.debug.databinding.UaItemPropertyBinding

class IdentifierAdapter(private val callback: ((identifier: Pair<String, String>) -> Unit)) : ListAdapter<Pair<String, String>,
        IdentifierAdapter.ViewHolder>(IdentifierAdapter.IdentifierFilterDiff()) {

    class ViewHolder(val binding: UaItemPropertyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        val binding = UaItemPropertyBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        getItem(i)?.let { identifierPair ->
            with(viewHolder.binding) {
                this.name = identifierPair.first

                root.setOnClickListener {
                    callback(identifierPair)
                }
                executePendingBindings()
            }
        }
    }

    class IdentifierFilterDiff : DiffUtil.ItemCallback<Pair<String, String>>() {

        override fun areItemsTheSame(s: Pair<String, String>, t1: Pair<String, String>): Boolean {
            return s === t1
        }

        override fun areContentsTheSame(s: Pair<String, String>, t1: Pair<String, String>): Boolean {
            return s == t1
        }
    }
}