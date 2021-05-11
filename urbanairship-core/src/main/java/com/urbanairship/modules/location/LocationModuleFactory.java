/* Copyright Airship and Contributors */

package com.urbanairship.modules.location;

import android.content.Context;

import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Location module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface LocationModuleFactory extends AirshipVersionInfo {

    @NonNull
    LocationModule build(@NonNull Context context,
                         @NonNull PreferenceDataStore dataStore,
                         @NonNull PrivacyManager privacyManager,
                         @NonNull AirshipChannel airshipChannel,
                         @NonNull Analytics analytics);

}
