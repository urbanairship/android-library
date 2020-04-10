/* Copyright Airship and Contributors */

package com.urbanairship.debug.customevent

import androidx.databinding.BindingAdapter
import com.google.android.material.textfield.TextInputLayout

@BindingAdapter("airshipErrorMessage")
fun bindErrorMessage(view: TextInputLayout, error: String?) {
    view.isErrorEnabled = !error.isNullOrEmpty()
    view.error = error
}
