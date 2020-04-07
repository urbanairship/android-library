/* Copyright Airship and Contributors */

package com.urbanairship.modules.messagecenter;

import android.content.Context;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.ModuleLoader;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Message Center module loader factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface MessageCenterModuleLoaderFactory {
    ModuleLoader build(@NonNull Context context,
                       @NonNull PreferenceDataStore dataStore,
                       @NonNull AirshipChannel airshipChannel);
}
