/* Copyright Airship and Contributors */

package com.urbanairship.push.adm;

import com.amazon.device.messaging.ADMMessageReceiver;
import com.urbanairship.Logger;
import androidx.annotation.RestrictTo;

/**
 * AdmPushReceiver listens for incoming ADM registration responses and messages.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AdmPushReceiver extends ADMMessageReceiver {

    public AdmPushReceiver() {
        super(AdmHandlerBase.class);

        //Check if the latest ADM version is available on the device
        try {
            Class.forName("com.amazon.device.messaging.ADMMessageHandlerJobBase");
            registerJobServiceClass(AdmHandlerJobBase.class, 3004);
        } catch (ClassNotFoundException e) {
            Logger.warn("Using legacy ADM class : " + e.getMessage());
        }
    }
}
