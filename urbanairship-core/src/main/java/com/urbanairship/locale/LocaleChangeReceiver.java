package com.urbanairship.locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.Autopilot;

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

        Autopilot.automaticTakeOff(context);
        LocaleManager.shared(context).notifyLocaleChanged();
    }

}
