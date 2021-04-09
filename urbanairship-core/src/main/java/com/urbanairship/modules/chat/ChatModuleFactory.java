/* Copyright Airship and Contributors */

package com.urbanairship.modules.chat;

import android.content.Context;

import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.Module;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Chat module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ChatModuleFactory extends AirshipVersionInfo {
    @NonNull
    Module build(@NonNull Context context,
                 @NonNull PreferenceDataStore dataStore,
                 @NonNull AirshipChannel airshipChannel);
}
