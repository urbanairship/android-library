package com.urbanairship.debug.utils

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil

/**
 * Item callback that checks the object equality.
 */
class SimpleDiffItemCallback<T> : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T & Any, newItem: T & Any): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: T & Any, newItem: T & Any): Boolean {
        return oldItem == newItem
    }
}
