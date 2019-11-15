/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import android.graphics.drawable.ColorDrawable
import android.text.format.DateUtils
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.databinding.BindingAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton

@BindingAdapter("fabVisibility")
fun bindFabVisibility(fab: FloatingActionButton, visible: Boolean) {
    if (visible) {
        fab.show()
    } else {
        fab.hide()
    }
}

@BindingAdapter("visible")
fun showHide(view: View, show: Boolean) {
    view.visibility = if (show) View.VISIBLE else View.GONE
}

@BindingAdapter("backgroundTint")
fun bindBackgroundTint(view: View, @ColorInt color: Int) {
    DrawableCompat.setTint(view.background, color)
}

@BindingAdapter("formatTime")
fun bindFormatTime(view: TextView, time: Long) {
    view.text = DateUtils.formatDateTime(view.context, time, DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)
}

@BindingAdapter("chipIconColor")
fun bindChipIconColor(view: Chip, @ColorInt color: Int) {
    view.chipIcon = ColorDrawable(color)
}

@BindingAdapter("eventFilter")
fun bindEventFilter(view: Chip, eventFilter: EventFilter) {
    view.text = EventInfo.getEventName(view.context, eventFilter.type)
    view.chipBackgroundColor = ContextCompat.getColorStateList(view.context, EventInfo.getColorRes(eventFilter.type))
}

@BindingAdapter("eventName")
fun bindEventName(view: TextView, item: EventItem) {
    view.text = EventInfo.getDetailedEventName(view.context, item.type, item.payload)
}

@BindingAdapter("checkedIconTint")
fun bindChipFilterIconTint(view: Chip, @ColorInt color: Int) {
    view.checkedIcon?.let {
        DrawableCompat.setTint(it, color)
    }
}
