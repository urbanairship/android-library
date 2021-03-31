/* Copyright Airship and Contributors */

package com.urbanairship.chat;

import android.content.Context;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.modules.Module;
import com.urbanairship.modules.chat.ChatModuleFactory;

import androidx.annotation.NonNull;

/**
 * Chat module factory implementation.
 *
 * @hide
 */
public class ChatModuleFactoryImpl implements ChatModuleFactory {

    @NonNull
    @Override
    public Module build(@NonNull Context context,
                        @NonNull PreferenceDataStore dataStore,
                        @NonNull AirshipChannel airshipChannel,
                        @NonNull NamedUser namedUser) {
        return Module.singleComponent(new AirshipChat(context, dataStore, airshipChannel, namedUser), 0);
    }

    @NonNull
    @Override
    public String getAirshipVersion() {
        return BuildConfig.AIRSHIP_VERSION;
    }

    @NonNull
    @Override
    public String getPackageVersion() {
        return BuildConfig.SDK_VERSION;
    }


}
