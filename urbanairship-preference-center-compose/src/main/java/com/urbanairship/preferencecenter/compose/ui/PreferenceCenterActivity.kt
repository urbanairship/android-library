package com.urbanairship.preferencecenter.compose.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.urbanairship.Airship
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.theme

/** `Activity` that displays a Preference Center composable UI. */
public class PreferenceCenterActivity: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        Autopilot.automaticTakeOff(application)

        if (!Airship.isTakingOff && !Airship.isFlying) {
            UALog.e("PreferenceCenterActivity - unable to create activity, takeOff not called.")
            finish()
            return
        }

        val id = PreferenceCenter.parsePreferenceCenterId(intent)
            ?: throw IllegalArgumentException("Missing required extra: EXTRA_ID")

        // Load the theme (or the default theme if none has been set)
        val theme = PreferenceCenter.shared().theme

        setContent {
            PreferenceCenterTheme(
                colors = if (isSystemInDarkTheme()) theme.darkColors else theme.lightColors,
                typography = theme.typography,
                dimens = theme.dimens,
                shapes = theme.shapes,
                options = theme.options
            ) {
                PreferenceCenterScreen(
                    identifier = id,
                    onNavigateUp = { finish() }
                )
            }
        }
    }
}
