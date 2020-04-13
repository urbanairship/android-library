/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo.attributes

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import java.util.Calendar

class DatePickerFragment : DialogFragment() {
    private lateinit var viewModel: AttributesViewModel

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel = ViewModelProvider(requireActivity()).get(AttributesViewModel::class.java)

        val calendar = Calendar.getInstance()
        calendar.time = viewModel.dateValue.value!!

        val callback = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            viewModel.dateValue.value = calendar.time
        }

        return DatePickerDialog(requireContext(), callback, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }
}
