package com.urbanairship.debug.contact.open

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class IdentifierViewModel(val initKey: String? = null, initValue: String? = null) : ViewModel() {
    val key = MutableLiveData<String>()
    val value = MutableLiveData<String>()

    val keyValidator = MediatorLiveData<Boolean>()
    val valueValidator = MediatorLiveData<Boolean>()

    init {
        this.key.value = initKey
        this.value.value = initValue

        keyValidator.value = true
        keyValidator.addSource(key) {
            clearKeyValidator()
        }

        valueValidator.value = true
        valueValidator.addSource(value) {
            clearValueValidator()
        }
    }

    private fun clearKeyValidator() {
        keyValidator.value = true
    }

    private fun clearValueValidator() {
        valueValidator.value = true
    }

    fun validate(): Boolean {
        return !key.value.isNullOrEmpty() && !value.value.isNullOrEmpty()
    }
}