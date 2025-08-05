/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.app.Activity
import android.os.Bundle
import androidx.annotation.RestrictTo
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship

/**
 * An activity that handles notification intents.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationProxyActivity public constructor() : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Autopilot.automaticTakeOff(this)

        if (!UAirship.isTakingOff && !UAirship.isFlying) {
            UALog.e("NotificationProxyActivity - unable to receive intent, takeOff not called.")
            finish()
            return
        }

        if (intent?.action == null) {
            finish()
            return
        }

        UALog.v("Received intent: %s", intent.action)

        NotificationIntentProcessor(context = this, intent = intent)
            .process()
            .addResultCallback {
                UALog.v("Finished processing notification intent with result $it.")
            }

        finish()
    }
}
