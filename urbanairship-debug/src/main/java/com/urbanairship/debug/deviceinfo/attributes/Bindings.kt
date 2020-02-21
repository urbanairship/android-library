/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.attributes

import android.text.format.DateFormat
import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.util.Date

@BindingAdapter("airshipDateText")
fun bindAirshipDateText(view: TextView, date: Date?) {
    view.text = date?.let {
        DateFormat.getLongDateFormat(view.context).format(date)
    }.orEmpty()
}

@BindingAdapter("airshipTimeText")
fun bindAirshipTimeText(view: TextView, date: Date?) {
    view.text = date?.let {
        DateFormat.getTimeFormat(view.context).format(date)
    }.orEmpty()
}
