/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Intent;
import android.support.annotation.CallSuper;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.google.android.gms.iid.InstanceIDListenerService;
import com.urbanairship.Logger;

/**
 * Listens for GCM Security token refresh. If your application needs to be notified when
 * the security tokens are refreshed, extend {@link UAInstanceIDListenerService} and override
 * {@link #onTokenRefresh()}. Make sure to call {@code super.onTokenRefresh()}.
 * <p/>
 * In the AndroidManifest.xml, add the following under the application entry:
 * <pre>
 * {@code
 * <service android:name="com.urbanairship.push.UAInstanceIDListenerService" tools:node="remove"/>
 * <service android:name=".YourInstanceIDListenerService" android:exported="false">
 *     <intent-filter>
 *         <action android:name="com.google.android.gms.iid.InstanceID"/>
 *     </intent-filter>
 * </service>
 * }
 * </pre>
 */
public class UAInstanceIDListenerService extends InstanceIDListenerService {

    @Override
    @CallSuper
    public void onTokenRefresh() {
        super.onTokenRefresh();

        Logger.debug("GCM security tokens refreshed.");

        Intent intent = new Intent(getApplicationContext(), PushService.class)
                .setAction(PushService.ACTION_UPDATE_PUSH_REGISTRATION)
                .putExtra(PushService.EXTRA_GCM_TOKEN_REFRESH, true);

        WakefulBroadcastReceiver.startWakefulService(getApplicationContext(), intent);
    }
}
