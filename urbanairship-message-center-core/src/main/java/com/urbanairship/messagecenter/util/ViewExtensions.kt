package com.urbanairship.messagecenter.util

import android.view.View
import android.widget.TextView

public fun TextView.setTextOrHide(text: String?) {
    this.text = text
    this.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
}
