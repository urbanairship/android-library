package com.urbanairship.debug.contact.email

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.urbanairship.json.JsonValue
import org.json.JSONArray
import org.json.JSONObject

class PropertyViewModel(val initName: String? = null, initValue: JsonValue? = null) : ViewModel() {
    val name = MutableLiveData<String>()

    val propertyType = MutableLiveData(PropertyType.STRING)

    val booleanValue = MutableLiveData(true)
    val stringValue = MutableLiveData<String>()
    val numberValue = MutableLiveData<String>()
    val jsonValue = MutableLiveData<String>()

    val nameValidator = MediatorLiveData<Boolean>()
    val valueValidator = MediatorLiveData<Boolean>()

    val value: JsonValue
        get() {
            return when (propertyType.value) {
                PropertyType.STRING -> JsonValue.wrapOpt(stringValue.value)
                PropertyType.NUMBER -> JsonValue.wrapOpt(numberValue.value?.toDouble())
                PropertyType.BOOLEAN -> JsonValue.wrapOpt(booleanValue.value)
                PropertyType.JSON -> JsonValue.parseString(jsonValue.value)
                else -> JsonValue.NULL
            }
        }

    init {
        this.name.value = initName

        initValue?.let { value ->
            when {
                value.isString -> {
                    propertyType.value = PropertyType.STRING
                    stringValue.value = value.optString()
                }
                value.isBoolean -> {
                    propertyType.value = PropertyType.BOOLEAN
                    booleanValue.value = value.getBoolean(false)
                }
                value.isNumber -> {
                    propertyType.value = PropertyType.NUMBER
                    numberValue.value = value.toString()
                }
                value.isJsonMap -> {
                    propertyType.value = PropertyType.JSON
                    jsonValue.value = JSONObject(value.toString()).toString(4)
                }
                value.isJsonList -> {
                    propertyType.value = PropertyType.JSON
                    jsonValue.value = JSONArray(value.toString()).toString(4)
                }
            }
        }

        nameValidator.value = true
        nameValidator.addSource(name) { clearNameValidator() }

        valueValidator.value = true
        valueValidator.addSource(propertyType) { clearValueValidator() }
        valueValidator.addSource(stringValue) { clearValueValidator() }
        valueValidator.addSource(numberValue) { clearValueValidator() }
        valueValidator.addSource(booleanValue) { clearValueValidator() }
        valueValidator.addSource(jsonValue) { clearValueValidator() }
    }

    override fun onCleared() {
        super.onCleared()
        valueValidator.removeSource(stringValue)
        valueValidator.removeSource(numberValue)
        valueValidator.removeSource(booleanValue)
        valueValidator.removeSource(jsonValue)
        nameValidator.removeSource(name)
    }

    fun toggleBoolean() {
        this.booleanValue.value = this.booleanValue.value?.not()
    }

    fun validate(): Boolean {
        val valueIsValid = when (propertyType.value) {
            PropertyType.STRING -> !stringValue.value.isNullOrBlank()
            PropertyType.NUMBER -> !numberValue.value.isNullOrBlank()
            PropertyType.BOOLEAN -> true
            PropertyType.JSON -> isJsonValid()
            else -> false
        }
        valueValidator.value = valueIsValid

        val nameIsValid = !name.value.isNullOrEmpty()
        nameValidator.value = nameIsValid

        return nameIsValid && valueIsValid
    }

    private fun clearNameValidator() {
        nameValidator.value = true
    }

    private fun clearValueValidator() {
        valueValidator.value = true
    }

    private fun isJsonValid(): Boolean {
        if (jsonValue.value.isNullOrBlank()) {
            return false
        }

        return try {
            JsonValue.parseString(jsonValue.value)
            true
        } catch (e: Exception) {
            false
        }
    }

}