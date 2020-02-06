package com.urbanairship.debug.deviceinfo.preferences

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.preference.Preference
import com.urbanairship.UAirship
import com.urbanairship.debug.extensions.copyToClipboard

class AirshipVersionPreference : Preference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    override fun getSummary(): String {
        return UAirship.getVersion()
    }

    override fun onClick() {
        super.onClick()
        summary.copyToClipboard(context, true)
    }
}
