/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo

import androidx.lifecycle.ViewModel

import com.urbanairship.UAirship
import com.urbanairship.channel.AttributeEditor


class DeviceInfoAttributesViewModel : ViewModel() {

    /**
     * Updates the attributes.
     */
    fun updateAttributes(isRemove:Boolean, key:String, value:String?) : Boolean {
        if (!validateInput(isRemove, key, value)) {
            return false
        }

        val attributeEditor:AttributeEditor = UAirship.shared().channel.editAttributes()

        if (isRemove) {
            attributeEditor.removeAttribute(key)
        } else {
            attributeEditor.setAttribute(key, value!!)
        }

        attributeEditor.apply()

        return true
    }

    private fun validateInput(isRemove:Boolean, key:String, value:String?) : Boolean {
        if (key.isEmpty() || key.count() > 1024) {
            return false
        }

        // Only pay attention to the key if action is set to remove
        if (isRemove) {
            return true
        }

        if (value == null || value.isEmpty() || value.count() > 1024) {
            return false
        }

        return true
    }
}
