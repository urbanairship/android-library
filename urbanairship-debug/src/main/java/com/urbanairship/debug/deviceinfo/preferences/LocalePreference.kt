package com.urbanairship.debug.deviceinfo.preferences

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.preference.Preference
import com.urbanairship.UAirship
import com.urbanairship.debug.extensions.copyToClipboard
import com.urbanairship.locale.LocaleChangedListener
import com.urbanairship.locale.LocaleManager
import java.lang.ref.WeakReference
import java.util.Locale

class LocalePreference : Preference {
    private val localeManager: LocaleManager = UAirship.shared().localeManager

    private val localeListener: LocaleChangedListener = LocaleChangedListener { refreshPreference(it) }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    private fun refreshPreference(locale: Locale) {
        val weakThis = WeakReference(this)
        val handler = Handler(Looper.getMainLooper())

        handler.post(Runnable {
            val preference = weakThis.get() ?: return@Runnable

            preference.summary = locale.toString()
        })
    }

    override fun onClick() {
        super.onClick()
        summary.copyToClipboard(context, true)
    }

    override fun onAttached() {
        super.onAttached()

        localeManager.addListener(localeListener)
        refreshPreference(localeManager.locale)
    }

    override fun onDetached() {
        super.onDetached()

        localeManager.removeListener(localeListener)
    }
}
