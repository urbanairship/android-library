/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.urbanairship.UAirship
import java.util.*

class DeviceInfoTagsViewModel : ViewModel() {

    private val tagsLiveData = MutableLiveData<List<String>>()
    private val tags: MutableList<String>

    /**
     * Default constructor.
     */
    init {
        this.tags = ArrayList(UAirship.shared().channel.tags)
        updateList()
    }

    /**
     * Gets the tags live data.
     *
     * @return The tags live data.
     */
    fun getTags(): LiveData<List<String>> {
        return tagsLiveData
    }

    /**
     * Adds a channel tag to Urban Airship.
     *
     * @param tag The tag.
     */
    fun addTag(tag: String) {
        UAirship.shared().channel.editTags().addTag(tag).apply()
        tags.add(tag)
        updateList()
    }

    /**
     * Removes a channel tag from Urban Airship.
     *
     * @param tag The tag.
     */
    fun removeTag(tag: String) {
        UAirship.shared().channel.editTags().removeTag(tag).apply()
        tags.remove(tag)
        updateList()
    }

    private fun updateList() {
        tagsLiveData.value = ArrayList(tags)
    }

}
