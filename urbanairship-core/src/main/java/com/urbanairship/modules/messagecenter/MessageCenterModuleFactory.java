/* Copyright Airship and Contributors */

package com.urbanairship.modules.messagecenter;

import android.content.Context;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.modules.Module;
import com.urbanairship.push.PushManager;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Message Center module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface MessageCenterModuleFactory extends AirshipVersionInfo {

    @NonNull
    Module build(@NonNull Context context,
                 @NonNull PreferenceDataStore dataStore,
                 @NonNull AirshipRuntimeConfig config,
                 @NonNull PrivacyManager privacyManager,
                 @NonNull AirshipChannel airshipChannel,
                 @NonNull PushManager pushManager);


}
