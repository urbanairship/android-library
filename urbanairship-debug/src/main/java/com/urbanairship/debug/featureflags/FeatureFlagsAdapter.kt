package com.urbanairship.debug.featureflags

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.debug.databinding.UaItemFeatureFlagBinding
import com.urbanairship.json.JsonMap
import com.urbanairship.json.requireField

/** Feature Flags list fragment. */
class FeatureFlagsAdapter : ListAdapter<JsonMap, FeatureFlagsAdapter.ViewHolder>(DIFF_UTIL) {

    var listener: ((featureFlag: JsonMap) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val parent: ViewGroup,
        private val binding: UaItemFeatureFlagBinding = UaItemFeatureFlagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(featureFlag: JsonMap): Unit = with(binding) {
            featureFlagName = featureFlag.requireField<JsonMap>("flag").requireField<String>("name")
            root.setOnClickListener {
                listener?.invoke(featureFlag)
            }

            executePendingBindings()
        }
    }

    companion object {
        private val DIFF_UTIL = object : DiffUtil.ItemCallback<JsonMap>() {
            override fun areItemsTheSame(old: JsonMap, new: JsonMap) = old === new
            override fun areContentsTheSame(old: JsonMap, new: JsonMap) = old == new
        }
    }
}
