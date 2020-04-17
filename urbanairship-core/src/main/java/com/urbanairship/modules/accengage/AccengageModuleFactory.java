/* Copyright Airship and Contributors */

package com.urbanairship.modules.accengage;

import android.content.Context;

import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.push.PushManager;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Accengage module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AccengageModuleFactory extends AirshipVersionInfo {

    @NonNull
    AccengageModule build(@NonNull Context context,
                          @NonNull PreferenceDataStore dataStore,
                          @NonNull AirshipChannel airshipChannel,
                          @NonNull PushManager manager,
                          @NonNull Analytics analytics);


}
