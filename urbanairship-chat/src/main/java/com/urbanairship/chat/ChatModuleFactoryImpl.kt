/* Copyright Airship and Contributors */

package com.urbanairship.chat

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.modules.Module
import com.urbanairship.modules.chat.ChatModuleFactory
import com.urbanairship.push.PushManager

/**
 * Chat module factory implementation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ChatModuleFactoryImpl : ChatModuleFactory {

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        pushManager: PushManager
    ): Module {
        val chat = Chat(context, dataStore, config, privacyManager, airshipChannel, pushManager)
        return Module.singleComponent(chat, R.xml.ua_chat_actions)
    }

    override fun getAirshipVersion(): String = BuildConfig.AIRSHIP_VERSION

    override fun getPackageVersion(): String = BuildConfig.SDK_VERSION
}
