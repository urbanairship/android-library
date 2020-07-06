/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.preferences

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import com.urbanairship.UAirship
import com.urbanairship.iam.InAppAutomation
import com.urbanairship.preference.UACheckBoxPreference

/**
 * CheckboxPreference to enable/disable IAA (In-App Automation).
 */
class InAppAutomationEnablePreference : UACheckBoxPreference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    override fun getInitialAirshipValue(airship: UAirship): Boolean {
        return InAppAutomation.shared().isEnabled
    }

    override fun onApplyAirshipPreference(airship: UAirship, enabled: Boolean) {
        InAppAutomation.shared().isEnabled = enabled
    }
}
