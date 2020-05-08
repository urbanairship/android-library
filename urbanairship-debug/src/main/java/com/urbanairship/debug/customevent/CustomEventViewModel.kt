/* Copyright Airship and Contributors */

package com.urbanairship.debug.customevent

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

class CustomEventViewModel : ViewModel() {

    private val propertiesMap = mutableMapOf<String, JsonValue>()

    val name = MutableLiveData<String>()
    val value = MutableLiveData<String>()
    val transactionId = MutableLiveData<String>()
    val interactionId = MutableLiveData<String>()
    val interactionType = MutableLiveData<String>()
    val properties = MutableLiveData<Map<String, JsonValue>>()

    val nameValidator = MediatorLiveData<Boolean>()
    val valueValidator = MediatorLiveData<Boolean>()
    val interactionIdValidator = MediatorLiveData<Boolean>()
    val interactionTypeValidator = MediatorLiveData<Boolean>()

    init {
        nameValidator.value = true
        valueValidator.value = true
        interactionIdValidator.value = true
        interactionTypeValidator.value = true

        nameValidator.addSource(name) { nameValidator.value = true }
        valueValidator.addSource(value) { valueValidator.value = true }
        interactionIdValidator.addSource(interactionType) { interactionIdValidator.value = true }
        interactionIdValidator.addSource(interactionId) { interactionIdValidator.value = true }
        interactionTypeValidator.addSource(interactionType) { interactionTypeValidator.value = true }
        interactionTypeValidator.addSource(interactionId) { interactionTypeValidator.value = true }
    }

    override fun onCleared() {
        super.onCleared()

        nameValidator.removeSource(name)
        valueValidator.removeSource(value)
        interactionTypeValidator.removeSource(interactionType)
        interactionTypeValidator.removeSource(interactionId)
        interactionIdValidator.removeSource(interactionType)
        interactionIdValidator.removeSource(interactionId)
    }

    fun removeProperty(name: String) {
        propertiesMap.remove(name)
        properties.value = propertiesMap
    }

    fun getProperty(name: String): JsonValue? {
        return propertiesMap[name]
    }

    fun addProperty(name: String, value: JsonValue) {
        propertiesMap[name] = value
        properties.value = propertiesMap
    }

    private fun validate(): Boolean {
        if (!interactionId.value.isNullOrEmpty() || !interactionType.value.isNullOrEmpty()) {
            interactionIdValidator.value = !interactionId.value.isNullOrEmpty()
            interactionTypeValidator.value = !interactionType.value.isNullOrEmpty()
        }
        nameValidator.value = !name.value.isNullOrBlank()
        valueValidator.value = !value.value.isNullOrBlank()

        return interactionIdValidator.value!! &&
                interactionTypeValidator.value!! &&
                nameValidator.value!! &&
                valueValidator.value!!
    }

    fun createEvent(): Boolean {
        if (!validate()) {
            return false
        }

        CustomEvent.newBuilder(name.value!!)
                .setEventValue(value.value!!.toDouble())
                .setInteraction(interactionId.value, interactionType.value)
                .setTransactionId(transactionId.value)
                .setProperties(JsonMap(propertiesMap))
                .build()
                .track()
        return true
    }
}
