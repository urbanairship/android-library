package com.urbanairship.debug.contact.open

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.urbanairship.UAirship
import com.urbanairship.contacts.OpenChannelRegistrationOptions

class OpenChannelViewModel : ViewModel() {
    private val identifiersMap = mutableMapOf<String, String>()
    val identifiers = MutableLiveData<Map<String, String>>()

    val address = MutableLiveData<String>()
    val platformName = MutableLiveData<String>()

    val addressValidator = MediatorLiveData<Boolean>()
    val platformNameValidator = MediatorLiveData<Boolean>()

    init {
        addressValidator.value = true
        platformNameValidator.value = true

        addressValidator.addSource(address) { addressValidator.value = true }
        platformNameValidator.addSource(platformName) { platformNameValidator.value = true }
    }

    override fun onCleared() {
        super.onCleared()

        addressValidator.removeSource(address)
        platformNameValidator.removeSource(platformName)
    }

    fun removeIdentifier(name: String) {
        identifiersMap.remove(name)
    }

    fun getIdentifier(name: String): String? {
        return identifiersMap[name]
    }

    fun addIdentifier(name: String, value: String) {
        identifiersMap[name] = value
        identifiers.value = identifiersMap
    }

    private fun validate(): Boolean {
        val addressIsValid = !address.value.isNullOrBlank()
        addressValidator.value = addressIsValid

        val platformIsValid = !platformName.value.isNullOrBlank()
        platformNameValidator.value = platformIsValid

        return addressIsValid && platformIsValid
    }

    fun associateOpenChannel(): Boolean {
        if (!validate()) {
            return false
        }

        var openChannelOptions: OpenChannelRegistrationOptions

        if (identifiers.value!!.isNotEmpty()) {
            openChannelOptions = OpenChannelRegistrationOptions.options(platformName.value!!, identifiers.value)
        } else {
            openChannelOptions = OpenChannelRegistrationOptions.options(platformName.value!!)
        }

        UAirship.shared().contact.registerOpenChannel(address.value!!, openChannelOptions)

        return true
    }
}