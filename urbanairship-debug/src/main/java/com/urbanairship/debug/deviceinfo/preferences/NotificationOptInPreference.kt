package com.urbanairship.debug.deviceinfo.preferences

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.preference.Preference
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.debug.R

class NotificationOptInPreference : Preference {

    private val channelListener = object : AirshipChannelListener {
        override fun onChannelUpdated(channelId: String) = postUpdate()
        override fun onChannelCreated(channelId: String) = postUpdate()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun getSummary(): CharSequence {
        return if (UAirship.shared().pushManager.areNotificationsOptedIn()) context.getString(R.string.ua_opted_in) else context.getString(R.string.ua_opted_out)
    }

    override fun onAttached() {
        super.onAttached()
        UAirship.shared().channel.addChannelListener(channelListener)
    }

    override fun onDetached() {
        super.onDetached()
        UAirship.shared().channel.removeChannelListener(channelListener)
    }

    private fun postUpdate() {
        val handler = Handler(Looper.getMainLooper())
        handler.post { notifyChanged() }
    }
}
