/* Copyright Airship and Contributors */

package com.urbanairship.push.adm;

import android.content.Context;
import android.content.Intent;

import com.amazon.device.messaging.ADMMessageHandlerJobBase;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProviderBridge;

public class AdmHandlerJobBase extends ADMMessageHandlerJobBase {

    @Override
    protected void onMessage(Context context, Intent intent) {
        Autopilot.automaticTakeOff(context);
        final PushMessage message = new PushMessage(intent.getExtras());
        PushProviderBridge.processPush(AdmPushProvider.class, message).executeSync(context);
    }

    @Override
    protected void onRegistrationError(Context context, String errorId) {
        Logger.error("An error occured during ADM Registration : " + errorId);
    }

    @Override
    protected void onRegistered(Context context, String newRegistrationId) {
        PushProviderBridge.requestRegistrationUpdate(context, AdmPushProvider.class, newRegistrationId);
    }

    @Override
    protected void onUnregistered(Context context, String newRegistrationId) {
        PushProviderBridge.requestRegistrationUpdate(context, AdmPushProvider.class, newRegistrationId);
    }

}
