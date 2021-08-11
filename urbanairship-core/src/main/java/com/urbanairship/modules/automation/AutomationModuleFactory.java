/* Copyright Airship and Contributors */

package com.urbanairship.modules.automation;

import android.content.Context;

import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.contacts.Contact;
import com.urbanairship.modules.Module;
import com.urbanairship.push.PushManager;
import com.urbanairship.remotedata.RemoteData;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Automation module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AutomationModuleFactory extends AirshipVersionInfo {

    @NonNull
    Module build(@NonNull Context context,
                 @NonNull PreferenceDataStore dataStore,
                 @NonNull AirshipRuntimeConfig runtimeConfig,
                 @NonNull PrivacyManager privacyManager,
                 @NonNull AirshipChannel airshipChannel,
                 @NonNull PushManager pushManager,
                 @NonNull Analytics analytics,
                 @NonNull RemoteData remoteData,
                 @NonNull Contact contact);

}
