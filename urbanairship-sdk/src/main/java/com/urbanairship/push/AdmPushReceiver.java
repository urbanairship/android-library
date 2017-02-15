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

        switch (intent.getAction()) {
            case ADMConstants.LowLevel.ACTION_RECEIVE_ADM_MESSAGE:
                PushProviderBridge.receivedPush(context, AdmPushProvider.class, intent.getExtras());
                break;

            case ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT:

                if (intent.getExtras().containsKey(ADMConstants.LowLevel.EXTRA_ERROR)) {
                    Logger.error("ADM error occurred: " + intent.getExtras().getString(ADMConstants.LowLevel.EXTRA_ERROR));
                }

                String registrationID = intent.getStringExtra(ADMConstants.LowLevel.EXTRA_REGISTRATION_ID);
                PushProviderBridge.registrationFinished(context, AdmPushProvider.class, registrationID);
                break;
        }

        if (isOrderedBroadcast()) {
            setResultCode(Activity.RESULT_OK);
        }
    }
}
