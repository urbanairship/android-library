package com.urbanairship.preferencecenter.compose.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.urbanairship.Airship
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.preferencecenter.PreferenceCenter

/** `Activity` that displays a Preference Center composable UI. */
public class PreferenceCenterActivity: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (!Airship.isTakingOff && !Airship.isFlying) {
            UALog.e("PreferenceCenterActivity - unable to create activity, takeOff not called.")
            finish()
            return
        }

        val id = PreferenceCenter.parsePreferenceCenterId(intent)
            ?: throw IllegalArgumentException("Missing required extra: EXTRA_ID")

        setContent {
            PreferenceCenterView(
                viewModel = DefaultPreferenceCenterViewModel(
                    preferenceCenterId = id
                ),
                onBackButton = { finish() }
            )
        }
    }
}
