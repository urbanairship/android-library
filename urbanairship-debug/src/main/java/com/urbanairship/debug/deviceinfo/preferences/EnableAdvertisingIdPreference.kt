/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.preferences

import android.content.Context
import android.util.AttributeSet
import com.urbanairship.UAirship
import com.urbanairship.aaid.AdvertisingIdTracker
import com.urbanairship.preference.UACheckBoxPreference

class EnableAdvertisingIdPreference : UACheckBoxPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    override fun getInitialAirshipValue(airship: UAirship): Boolean {
        return AdvertisingIdTracker.shared().isEnabled
    }

    override fun onApplyAirshipPreference(airship: UAirship, enabled: Boolean) {
        AdvertisingIdTracker.shared().isEnabled = enabled
    }
}
