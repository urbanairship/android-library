/* Copyright Airship and Contributors */

package com.urbanairship.push.adm;

import android.content.Intent;

import com.amazon.device.messaging.ADMMessageHandlerBase;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProviderBridge;

public class AdmHandlerBase extends ADMMessageHandlerBase {

    //Class that is used for legacy ADM devices
    public AdmHandlerBase(String className) {
        super(className);
    }
    public AdmHandlerBase() {
        super("AdmHandlerBase");
    }

    @Override
    protected void onMessage(Intent intent) {
        Autopilot.automaticTakeOff(getApplicationContext());
        final PushMessage message = new PushMessage(intent.getExtras());
        PushProviderBridge.processPush(AdmPushProvider.class, message).executeSync(getApplicationContext());
    }

    @Override
    protected void onRegistrationError(String errorId) {
        Logger.error("An error occured during ADM Registration : " + errorId);
    }

    @Override
    protected void onRegistered(String newRegistrationId) {
        PushProviderBridge.requestRegistrationUpdate(getApplicationContext(), AdmPushProvider.class, newRegistrationId);
    }

    @Override
    protected void onUnregistered(String newRegistrationId) {
        PushProviderBridge.requestRegistrationUpdate(getApplicationContext(), AdmPushProvider.class, newRegistrationId);
    }

}
