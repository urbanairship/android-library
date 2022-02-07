package com.urbanairship.debug.preferencecenter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.debug.databinding.UaItemPrefCenterBinding
import com.urbanairship.debug.preferencecenter.PrefCentersViewModel.PrefCenter

/** Preference Center List Fragment */
class PrefCentersAdapter : ListAdapter<PrefCenter, PrefCentersAdapter.ViewHolder>(DIFF_UTIL) {

    var listener: ((PrefCenter) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val parent: ViewGroup,
        private val binding: UaItemPrefCenterBinding = UaItemPrefCenterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(prefCenter: PrefCenter) = with(binding) {
            setPrefCenter(prefCenter)
            root.setOnClickListener {
                listener?.invoke(prefCenter)
            }
            executePendingBindings()
        }
    }

    companion object {
        private val DIFF_UTIL = object : DiffUtil.ItemCallback<PrefCenter>() {
            override fun areItemsTheSame(old: PrefCenter, new: PrefCenter) = old.id == new.id
            override fun areContentsTheSame(old: PrefCenter, new: PrefCenter) = old == new
        }
    }
}
