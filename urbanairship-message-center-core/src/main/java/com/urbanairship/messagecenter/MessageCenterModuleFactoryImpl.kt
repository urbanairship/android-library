package com.urbanairship.messagecenter

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.actions.ActionsManifest
import com.urbanairship.analytics.Analytics
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.messagecenter.actions.MessageCenterAction
import com.urbanairship.messagecenter.core.BuildConfig
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.modules.Module
import com.urbanairship.modules.messagecenter.MessageCenterModuleFactory
import com.urbanairship.push.PushManager

/**
 * Message Center module loader factory implementation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class MessageCenterModuleFactoryImpl public constructor() : MessageCenterModuleFactory {
    override val airshipVersion: String =  BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        pushManager: PushManager,
        analytics: Analytics,
        meteredUsage: AirshipMeteredUsage
    ): Module {
        val messageCenter =
            MessageCenter(
                context = context,
                dataStore = dataStore,
                config = config,
                privacyManager = privacyManager,
                channel = airshipChannel,
                pushManager = pushManager,
                analytics = analytics,
                meteredUsage = meteredUsage
            )
        return Module.Companion.singleComponent(messageCenter, MessageCenterActionsManifest())
    }
}

private class MessageCenterActionsManifest: ActionsManifest {

    override val manifest: Map<Set<String>, () -> ActionRegistry.Entry> = mapOf(
        MessageCenterAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(
                action = MessageCenterAction()
            )
        }
    )
}
