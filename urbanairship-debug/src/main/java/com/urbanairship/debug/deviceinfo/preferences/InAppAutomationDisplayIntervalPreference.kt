package com.urbanairship.debug.deviceinfo.preferences

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.preference.DialogPreference
import com.urbanairship.automation.rewrite.InAppAutomation
import com.urbanairship.debug.R
import java.util.concurrent.TimeUnit

/**
 * In-App Automation Display Interval preference.
 */
class InAppAutomationDisplayIntervalPreference : DialogPreference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    private fun getValue(): Long {
        return TimeUnit.SECONDS.convert(InAppAutomation.shared().inAppMessaging.displayInterval, TimeUnit.MILLISECONDS)
    }

    override fun getSummary(): CharSequence {
        return (getValue()).toString() + " " + context.getString(R.string.ua_iaa_display_interval_unit_time)
    }

    fun setValue(v: Long) {
        if (v != getValue()) {
            InAppAutomation.shared().inAppMessaging.displayInterval = TimeUnit.SECONDS.toMillis(v)
            notifyChanged()
        }
    }

    override fun getDialogLayoutResource(): Int {
        return R.layout.ua_fragment_display_interval
    }
}
