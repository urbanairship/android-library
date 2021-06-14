package com.urbanairship.modules.preferencecenter;

import android.content.Context;

import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.modules.Module;
import com.urbanairship.remotedata.RemoteData;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * PreferenceCenter module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface PreferenceCenterModuleFactory extends AirshipVersionInfo {

    @NonNull
    Module build(
            @NonNull Context Context,
            @NonNull PreferenceDataStore dataStore,
            @NonNull PrivacyManager privacyManager,
            @NonNull RemoteData remoteData
    );
}
