/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

internal interface Validatable {

    val isRequired: Boolean
    val isValid: Boolean
}
