package com.urbanairship.debug.utils

import androidx.recyclerview.widget.DiffUtil

/**
 * Item callback that checks the object equality.
 */
class SimpleDiffItemCallback<T> : DiffUtil.ItemCallback<T>() {

    override fun areItemsTheSame(t: T, t1: T): Boolean {
        return t == t1
    }

    override fun areContentsTheSame(t: T, t1: T): Boolean {
        return t == t1
    }

}
