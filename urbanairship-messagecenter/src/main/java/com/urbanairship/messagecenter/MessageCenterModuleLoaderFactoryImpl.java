package com.urbanairship.messagecenter;

import android.content.Context;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.ModuleLoader;
import com.urbanairship.modules.SimpleModuleLoader;
import com.urbanairship.modules.messagecenter.MessageCenterModuleLoaderFactory;
import com.urbanairship.push.PushManager;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Message Center module loader factory implementation.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class MessageCenterModuleLoaderFactoryImpl implements MessageCenterModuleLoaderFactory {

    @Override
    public ModuleLoader build(@NonNull Context context, @NonNull PreferenceDataStore dataStore, @NonNull AirshipChannel airshipChannel, @NonNull PushManager pushManager) {
        MessageCenter messageCenter = new MessageCenter(context, dataStore, airshipChannel, pushManager);
        return SimpleModuleLoader.singleComponent(messageCenter);
    }
}
