/* Copyright Airship and Contributors */
package com.urbanairship.devapp

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.webkit.WebView
import com.urbanairship.devapp.thomas.LayoutPreferenceManager

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        LayoutPreferenceManager.init(this)

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )

            StrictMode.setVmPolicy(
                VmPolicy.Builder().detectAll().penaltyLog().build()
            )

            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
