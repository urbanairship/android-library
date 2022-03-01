package com.urbanairship.debug.contact.sms

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.urbanairship.UAirship
import com.urbanairship.contacts.SmsRegistrationOptions

class SmsAssociateViewModel : ViewModel() {
    val msisdn = MutableLiveData<String>()
    val senderId = MutableLiveData<String>()

    val smsValidator = MediatorLiveData<Boolean>()

    init {
        smsValidator.value = true
        smsValidator.addSource(msisdn) { smsValidator.value = true }
        smsValidator.addSource(senderId) { smsValidator.value = true }
    }

    override fun onCleared() {
        super.onCleared()

        smsValidator.removeSource(msisdn)
        smsValidator.removeSource(senderId)
    }

    private fun validate(): Boolean {
        smsValidator.value = !msisdn.value.isNullOrBlank() && !senderId.value.isNullOrBlank()

        return smsValidator.value!!
    }

    fun associateToContact(): Boolean {
        if (!validate()) {
            return false
        }

        val smsOptions = SmsRegistrationOptions.options(senderId.value!!)

        UAirship.shared().contact.registerSms(msisdn.value!!, smsOptions)

        return true
    }
}