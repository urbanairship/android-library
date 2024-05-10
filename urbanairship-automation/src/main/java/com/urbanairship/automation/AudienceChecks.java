/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;

import com.urbanairship.audience.AudienceSelector;
import com.urbanairship.audience.DeviceInfoProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Audience checks.
 * @deprecated Internal class.
 */
@Deprecated
public abstract class AudienceChecks {

    /**
     * Checks the audience.
     *
     * @param context The application context.
     * @param audience The audience.
     * @return {@code true} if the audience conditions are met, otherwise {@code false}.
     */
    @WorkerThread
    public static boolean checkAudience(@NonNull Context context, @Nullable Audience audience) {
        if (audience == null) {
            return true;
        }

        AudienceSelector selector = audience.getAudienceSelector();
        if (selector == null) {
            return true;
        }

        try {
            return selector.evaluateAsPendingResult(0,
                    DeviceInfoProvider.newProvider(), null).get();
        } catch (Exception e) {
            return false;
        }
    }
}
