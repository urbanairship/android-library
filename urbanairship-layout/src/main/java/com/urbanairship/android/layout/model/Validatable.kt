/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.json.JsonMap

internal interface Validatable {

    val isRequired: Boolean
    val isValid: Boolean

    companion object {
        @JvmStatic
        fun requiredFromJson(json: JsonMap): Boolean = json.opt("required").getBoolean(false)
    }
}
