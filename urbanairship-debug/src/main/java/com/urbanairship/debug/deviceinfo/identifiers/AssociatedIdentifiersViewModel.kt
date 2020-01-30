/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo.identifiers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.urbanairship.UAirship

class AssociatedIdentifiersViewModel : ViewModel() {

    private val mutableIdentifiers = MutableLiveData<List<AssociatedIdentifier>>()

    val identifiers : LiveData<List<AssociatedIdentifier>>
        get() = mutableIdentifiers

    val key = MutableLiveData<String>()
    val value = MutableLiveData<String>()
    val formValidator = MediatorLiveData<Boolean>()

    init {
        formValidator.value = false
        formValidator.addSource(key) {
            formValidator.value = isFormValid()
        }
        formValidator.addSource(value) {
            formValidator.value = isFormValid()
        }
    }

    override fun onCleared() {
        super.onCleared()
        formValidator.removeSource(value)
        formValidator.removeSource(key)
    }

    init {
        updateList()
    }

    private fun isFormValid() :Boolean {
        return !key.value.isNullOrEmpty() && !value.value.isNullOrEmpty();
    }

    fun add() {
        assert(isFormValid())

        UAirship.shared().analytics.editAssociatedIdentifiers().addIdentifier(key.value!!, value.value!!).apply()
        key.value = null
        value.value = null
        updateList()
    }

    fun remove(key: String) {
        UAirship.shared().analytics.editAssociatedIdentifiers().removeIdentifier(key).apply()
        updateList()
    }

    private fun updateList() {
        this.mutableIdentifiers.value = UAirship.shared().analytics.associatedIdentifiers.ids.map { AssociatedIdentifier(it.key, it.value) }
    }
}
