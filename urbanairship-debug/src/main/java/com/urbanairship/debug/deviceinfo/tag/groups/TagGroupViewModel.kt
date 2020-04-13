/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo.tag.groups

import androidx.databinding.ObservableField
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.urbanairship.UAirship
import com.urbanairship.channel.TagGroupsEditor

class TagGroupViewModel : ViewModel() {

    val tagGroup = MutableLiveData<String>()
    val tag = MutableLiveData<String>()
    val tagGroupType = ObservableField(TagGroupType.CHANNEL)
    val formValidator = MediatorLiveData<Boolean>()

    init {
        formValidator.value = false
        formValidator.addSource(tag) {
            formValidator.value = isValid()
        }
        formValidator.addSource(tagGroup) {
            formValidator.value = isValid()
        }
    }

    override fun onCleared() {
        super.onCleared()
        formValidator.removeSource(tagGroup)
        formValidator.removeSource(tag)
    }

    fun addTag() {
        assert(isValid())
        tagEditor().addTag(tagGroup.value!!, tag.value!!).apply()
        tag.value = null
    }

    fun removeTag() {
        assert(isValid())
        tagEditor().removeTag(tagGroup.value!!, tag.value!!).apply()
        tag.value = null
    }

    private fun isValid(): Boolean {
        return !tag.value.isNullOrEmpty() && !tagGroup.value.isNullOrEmpty()
    }

    private fun tagEditor(): TagGroupsEditor {
        return when (tagGroupType.get()!!) {
            TagGroupType.CHANNEL -> {
                UAirship.shared().channel.editTagGroups()
            }
            TagGroupType.NAMED_USER -> {
                UAirship.shared().namedUser.editTagGroups()
            }
        }
    }
}
