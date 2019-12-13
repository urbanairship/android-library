package com.urbanairship.debug.deviceinfo.preferences

import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import androidx.preference.PreferenceDialogFragmentCompat
import com.urbanairship.UAirship
import com.urbanairship.debug.R
import java.util.concurrent.TimeUnit

/**
 * PreferenceDialogFragment to set the IAA (In-App Automation) Display Interval
 */
class InAppAutomationDisplayIntervalPreferenceDialogFragment : PreferenceDialogFragmentCompat() {

    private var numberPicker: NumberPicker? = null

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        numberPicker = view.findViewById(R.id.display_interval)


        numberPicker?.apply {
            minValue = MIN_VALUE
            maxValue = MAX_VALUE

            val seconds = UAirship.shared().inAppMessagingManager.displayInterval / 1000L
            value = seconds.toInt()
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            numberPicker?.clearFocus()
            saveDisplayInterval()
        }
    }

    /**
     * Save the display interval in milliseconds
     */
    private fun saveDisplayInterval() {
        var inAppPreference  = preference as InAppAutomationDisplayIntervalPreference?
        inAppPreference?.setValue(numberPicker!!.value.toLong())
    }


    companion object {

        private const val MAX_VALUE = 120
        private const val MIN_VALUE = 0

        /**
         * Create a new instance of InAppAutomationDisplayIntervalPreferenceDialogFragment
         * @param key the preference key
         * @return a new instance of InAppAutomationDisplayIntervalPreferenceDialogFragment
         */
        fun newInstance(key: String): InAppAutomationDisplayIntervalPreferenceDialogFragment {
            val dialogFragment = InAppAutomationDisplayIntervalPreferenceDialogFragment()
            val b = Bundle(1)
            b.putString(ARG_KEY, key)
            dialogFragment.arguments = b
            return dialogFragment
        }
    }

}
