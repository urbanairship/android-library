/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo.attributes

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import androidx.fragment.app.DialogFragment
import androidx.navigation.navGraphViewModels
import com.urbanairship.debug.R
import java.util.Calendar

class TimePickerFragment : DialogFragment() {

    val viewModel: AttributesViewModel by navGraphViewModels(R.id.ua_debug_device_info_navigation)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val calendar = Calendar.getInstance()
        calendar.time = viewModel.dateValue.value!!

        val callback = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            viewModel.dateValue.value = calendar.time
        }

        val is24Hour = DateFormat.is24HourFormat(requireContext())
        return TimePickerDialog(requireContext(), callback, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), is24Hour)
    }
}
