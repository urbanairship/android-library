package com.urbanairship.locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Broadcast receiver that listens for {@link Intent#ACTION_LOCALE_CHANGED}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LocaleChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(@NonNull final Context context, @Nullable final Intent intent) {
        if (intent == null || !Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            return;
        }

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("LocaleChangedReceiver - unable to receive intent, takeOff not called.");
            return;
        }

        Autopilot.automaticTakeOff(context);
        UAirship.shared().getLocaleManager().onDeviceLocaleChanged();
    }

}
