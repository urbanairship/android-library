/* Copyright Airship and Contributors */

package com.urbanairship.push.hms;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Airship HMS message service.
 */
public class AirshipHmsMessageService extends HmsMessageService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        AirshipHmsIntegration.processMessageSync(getApplicationContext(), message);
    }

    @Override
    public void onNewToken(@Nullable String string) {
        AirshipHmsIntegration.processNewToken(getApplicationContext());
    }
}
