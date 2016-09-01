/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.amazon.device.messaging.ADMConstants;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;

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

                Job messageJob = Job.newBuilder(PushJobHandler.ACTION_RECEIVE_ADM_MESSAGE)
                                    .setAirshipComponent(PushManager.class)
                                    .setExtras(intent.getExtras())
                                    .build();

                JobDispatcher.shared(context).wakefulDispatch(messageJob);
                break;

            case ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT:

                Job registrationJob = Job.newBuilder(ChannelJobHandler.ACTION_ADM_REGISTRATION_FINISHED)
                                         .setAirshipComponent(PushManager.class)
                                         .setExtras(intent.getExtras())
                                         .build();

                JobDispatcher.shared(context).wakefulDispatch(registrationJob);
                break;
        }

        if (isOrderedBroadcast()) {
            setResultCode(Activity.RESULT_OK);
        }
    }
}
