package com.urbanairship.debug.contact.email

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.urbanairship.UAirship
import com.urbanairship.contacts.EmailRegistrationOptions
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import java.util.Date

class EmailAssociateViewModel : ViewModel() {
    private val propertiesMap = mutableMapOf<String, JsonValue>()

    private var isTransactional = false
    private var isCommercial = false

    val email = MutableLiveData<String>()
    val properties = MutableLiveData<Map<String, JsonValue>>()

    val emailValidator = MediatorLiveData<Boolean>()

    init {
        emailValidator.value = true
        emailValidator.addSource(email) { emailValidator.value = true }
    }

    override fun onCleared() {
        super.onCleared()

        emailValidator.removeSource(email)
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

    fun setTransactional(isTransactional: Boolean) {
        this.isTransactional = isTransactional
    }

    fun setCommerical(isCommercial: Boolean) {
        this.isCommercial = isCommercial
    }

    private fun validate(): Boolean {
        val isValid = !email.value.isNullOrBlank()
        emailValidator.value = isValid

        return isValid
    }

    fun associateToContact(): Boolean {
        if (!validate()) {
            return false
        }
        val currentTime = System.currentTimeMillis();

        val commercialOptedInDate = if (isCommercial) { Date(currentTime) } else { null }
        val transactionalOptedInDate = if (isTransactional) { Date(currentTime) } else { null }

        val emailOptions = EmailRegistrationOptions.commercialOptions(commercialOptedInDate, transactionalOptedInDate, JsonMap(propertiesMap))
        UAirship.shared().contact.registerEmail(email.value!!,emailOptions)

        return true
    }
}