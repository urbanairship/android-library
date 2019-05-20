package com.urbanairship.debug.deviceinfo.preferences

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.support.v7.preference.Preference
import android.util.AttributeSet

import com.urbanairship.UAirship
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.copyToClipboard
import com.urbanairship.push.RegistrationListener

class PushTokenPreference : Preference {

    private val registrationListener = object : RegistrationListener {
        override fun onChannelCreated(channelId: String) {}

        override fun onChannelUpdated(channelId: String) {}

        override fun onPushTokenUpdated(token: String) { notifyChanged() }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    override fun getSummary(): CharSequence {
        return UAirship.shared().pushManager.pushToken ?: context.getString(R.string.ua_none)
    }

    override fun onClick() {
        super.onClick()
        summary.copyToClipboard(context, true)
    }

    override fun onAttached() {
        super.onAttached()
        UAirship.shared().pushManager.addRegistrationListener(registrationListener)
    }

    override fun onDetached() {
        super.onDetached()
        UAirship.shared().pushManager.removeRegistrationListener(registrationListener)
    }
}
