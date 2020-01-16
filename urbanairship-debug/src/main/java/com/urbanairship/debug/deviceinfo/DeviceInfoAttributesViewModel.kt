/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo

import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

import com.urbanairship.UAirship

class DeviceInfoAttributesViewModel : ViewModel()  {

    val key = MutableLiveData<String>()
    val value = MutableLiveData<String>()
    val attributeType = ObservableField<AttributeType>(AttributeType.STRING)
    val keyValidator = MediatorLiveData<Boolean>()
    val valueValidator = MediatorLiveData<Boolean>()

    private val attributeChangeListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            value.value = null
        }
    }

    init {
        keyValidator.value = false
        keyValidator.addSource(key) {
            keyValidator.value = !key.value.isNullOrEmpty()
        }

        valueValidator.value = false
        valueValidator.addSource(value) {
            valueValidator.value = when(attributeType.get()) {
                AttributeType.STRING -> !value.value.isNullOrEmpty()
                AttributeType.NUMBER -> value.value?.toDoubleOrNull() != null
                else -> false
            }
        }

        attributeType.addOnPropertyChangedCallback(attributeChangeListener)
    }

    override fun onCleared() {
        super.onCleared()
        valueValidator.removeSource(value)
        keyValidator.removeSource(key)
        attributeType.removeOnPropertyChangedCallback(attributeChangeListener)
    }

    fun setAttribute() {
        assert(keyValidator.value!!)
        assert(valueValidator.value!!)

        when(attributeType.get()) {
            AttributeType.STRING -> {
                UAirship.shared().channel.editAttributes()
                        .setAttribute(key.value!!, value.value!!)
                        .apply()
            }
            AttributeType.NUMBER -> {
                UAirship.shared().channel.editAttributes()
                        .setAttribute(key.value!!, value.value!!.toDouble())
                        .apply()
            }
        }
        clearForm()
    }

    fun removeAttribute() {
        assert(keyValidator.value!!)
        UAirship.shared().channel.editAttributes().removeAttribute(key.value!!).apply()
        clearForm()
    }

    fun clearForm() {
        key.value = null
        value.value = null
    }
}
