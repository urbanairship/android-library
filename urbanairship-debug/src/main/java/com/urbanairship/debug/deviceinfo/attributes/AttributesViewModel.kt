/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.attributes

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.urbanairship.UAirship
import java.util.Date

class AttributesViewModel : ViewModel() {

    val key = MutableLiveData<String>()
    val stringValue = MutableLiveData<String>()
    val numberValue = MutableLiveData<String>()
    val dateValue = MutableLiveData<Date>()

    val attributeType = MutableLiveData<AttributeType>(AttributeType.STRING)
    val keyValidator = MediatorLiveData<Boolean>()
    val valueValidator = MediatorLiveData<Boolean>()

    init {
        keyValidator.value = false
        keyValidator.addSource(key) {
            keyValidator.value = !key.value.isNullOrEmpty()
        }

        valueValidator.value = false
        valueValidator.addSource(stringValue) { validateFormValue() }
        valueValidator.addSource(numberValue) { validateFormValue() }
        valueValidator.addSource(dateValue) { validateFormValue() }
        valueValidator.addSource(attributeType) { validateFormValue() }

        dateValue.value = Date()
    }

    override fun onCleared() {
        super.onCleared()
        valueValidator.removeSource(stringValue)
        valueValidator.removeSource(dateValue)
        valueValidator.removeSource(numberValue)
        valueValidator.removeSource(attributeType)
        keyValidator.removeSource(key)
    }

    fun setAttribute() {
        assert(keyValidator.value!!)
        assert(valueValidator.value!!)

        when (attributeType.value) {
            AttributeType.STRING -> {
                UAirship.shared().channel.editAttributes().setAttribute(key.value!!, stringValue.value!!).apply()
            }
            AttributeType.NUMBER -> {
                UAirship.shared().channel.editAttributes().setAttribute(key.value!!, numberValue.value!!.toDouble()).apply()
            }
            AttributeType.DATE -> {
                UAirship.shared().channel.editAttributes().setAttribute(key.value!!, dateValue.value!!).apply()
            }
        }
    }

    private fun validateFormValue() {
        valueValidator.value = when (attributeType.value) {
            AttributeType.STRING -> !stringValue.value.isNullOrEmpty()
            AttributeType.NUMBER -> numberValue.value?.toDoubleOrNull() != null
            AttributeType.DATE -> dateValue.value != null
            else -> false
        }
    }

    fun removeAttribute() {
        assert(keyValidator.value!!)
        UAirship.shared().channel.editAttributes().removeAttribute(key.value!!).apply()
    }
}
