package com.urbanairship.preferencecenter.util

import android.view.View
import android.widget.TextView

internal fun TextView.setTextOrHide(text: String?) {
    this.text = text
    this.visibility = if (text != null) View.VISIBLE else View.GONE
}
