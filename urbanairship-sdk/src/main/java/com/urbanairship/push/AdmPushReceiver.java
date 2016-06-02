/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.amazon.device.messaging.ADMConstants;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;

/**
 * AdmPushReceiver listens for incoming ADM registration responses and messages.
 */
public class AdmPushReceiver extends WakefulBroadcastReceiver {

    @SuppressLint("NewApi")
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Autopilot.automaticTakeOff(context);

        if (intent == null || intent.getAction() == null) {
            return;
        }

        Logger.verbose("AdmPushReceiver - Received intent: " + intent.getAction());

        if (Build.VERSION.SDK_INT < 15) {
            Logger.error("AdmPushReceiver - Received intent from ADM transport on an unsupported API version.");
            return;
        }

        switch (intent.getAction()) {
            case ADMConstants.LowLevel.ACTION_RECEIVE_ADM_MESSAGE:
                Intent pushIntent = new Intent(context, PushService.class)
                        .setAction(PushService.ACTION_RECEIVE_ADM_MESSAGE)
                        .putExtra(PushService.EXTRA_INTENT, intent);

                startWakefulService(context, pushIntent);
                break;
            case ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT:
                Intent finishIntent = new Intent(context, PushService.class)
                        .setAction(PushService.ACTION_ADM_REGISTRATION_FINISHED)
                        .putExtra(PushService.EXTRA_INTENT, intent);

                startWakefulService(context, finishIntent);
                break;
        }

        if (isOrderedBroadcast()) {
            setResultCode(Activity.RESULT_OK);
        }
    }
}
