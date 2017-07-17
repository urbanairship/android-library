/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.amazon.device.messaging.ADMConstants;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.push.adm.AdmPushProvider;

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

        final boolean isOrderedBroadcast = isOrderedBroadcast();
        final PendingResult result;

        switch (intent.getAction()) {
            case ADMConstants.LowLevel.ACTION_RECEIVE_ADM_MESSAGE:

                result = goAsync();
                PushProviderBridge.receivedPush(context, AdmPushProvider.class, new PushMessage(intent.getExtras()), new Runnable() {
                    @Override
                    public void run() {
                        if (isOrderedBroadcast) {
                            result.setResultCode(Activity.RESULT_OK);
                        }
                        result.finish();
                    }
                });
                break;

            default:
                if (isOrderedBroadcast) {
                    setResultCode(Activity.RESULT_OK);
                }
        }
    }
}
