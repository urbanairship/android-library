/* Copyright Airship and Contributors */

package com.urbanairship.modules.aaid;

import android.content.Context;

import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.modules.Module;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Ad Id module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AdIdModuleFactory extends AirshipVersionInfo {

    @NonNull
    Module build(@NonNull Context context,
                 @NonNull PreferenceDataStore dataStore,
                 @NonNull AirshipRuntimeConfig runtimeConfig,
                 @NonNull PrivacyManager privacyManager,
                 @NonNull Analytics analytics);
}
