/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

/**
 * Tracks Google Play Store install referrals. The receiver needs to be added
 * to the manifest before the install referrals will be tracked:
 * <pre>
 * {@code
 * <receiver android:name="com.urbanairship.analytics.InstallReceiver" exported="true">
 *     <intent-filter>
 *         <action android:name="com.android.vending.INSTALL_REFERRER" />
 *     </intent-filter>
 * </receiver>
 * }
 * </pre>
 *
 * Only a single receiver is able to handle the {@code "com.android.vending.INSTALL_REFERRER"} action.
 * To handle multiple receivers, instead of registering the {@link InstallReceiver}, register a custom
 * receiver that notifies multiple receivers:
 * <pre>
 * {@code
 * // Notify the Urban Airship InstallReceiver
 * new InstallReceiver().onReceive(context, intent);
 *
 * // Notify other receivers
 * new OtherReceiver().onReceive(context, intent);
 * }
 * </pre>
 */
public class InstallReceiver extends BroadcastReceiver {

    private static final String EXTRA_INSTALL_REFERRER = "referrer";
    private static final String ACTION_INSTALL_REFERRER = "com.android.vending.INSTALL_REFERRER";

    @Override
    public void onReceive(Context context, Intent intent) {
        Autopilot.automaticTakeOff(context);
        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("InstallReceiver - unable to track install referrer, takeOff not called.");
            return;
        }

        String referrer = intent.getStringExtra(EXTRA_INSTALL_REFERRER);
        if (UAStringUtil.isEmpty(referrer) || !ACTION_INSTALL_REFERRER.equals(intent.getAction())) {
            Logger.debug("InstallReceiver - missing referrer or invalid action.");
            return;
        }

        InstallAttributionEvent event = new InstallAttributionEvent(referrer);
        UAirship.shared().getAnalytics().addEvent(event);
    }

}
