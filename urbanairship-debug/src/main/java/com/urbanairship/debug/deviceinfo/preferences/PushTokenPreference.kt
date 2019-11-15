package com.urbanairship.debug.deviceinfo.preferences

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.preference.Preference
import com.urbanairship.UAirship
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.copyToClipboard
import com.urbanairship.push.PushTokenListener

class PushTokenPreference : Preference {

    private val pushTokenListener = object : PushTokenListener {
        override fun onPushTokenUpdated(token: String) = postUpdate()
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
        UAirship.shared().pushManager.addPushTokenListener(pushTokenListener)
    }

    override fun onDetached() {
        super.onDetached()
        UAirship.shared().pushManager.removePushTokenListener(pushTokenListener)
    }

    private fun postUpdate() {
        val handler = Handler(Looper.getMainLooper())
        handler.post { notifyChanged() }
    }
}
