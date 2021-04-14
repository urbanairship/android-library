/* Copyright Airship and Contributors */

package com.urbanairship.chat

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig

/**
 * Airship Chat.
 */
class AirshipChat

/**
 * Full constructor (for tests).
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) @VisibleForTesting internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    val conversation: Conversation
) : AirshipComponent(context, dataStore) {

    companion object {
        // PreferenceDataStore keys
        private const val ENABLED_KEY = "com.urbanairship.chat.CHAT"

        /**
         * Gets the shared `AirshipChat` instance.
         *
         * @return an instance of `AirshipChat`.
         */
        @JvmStatic
        fun shared(): AirshipChat {
            return UAirship.shared().requireComponent(AirshipChat::class.java)
        }
    }

    /**
     * "Default" convenience constructor.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        channel: AirshipChannel
    ) : this(context, dataStore, Conversation(context, dataStore, config, channel))

    /**
     * Enables or disables Airship Chat.
     *
     * The value is persisted in shared preferences.
     */
    var isEnabled: Boolean
        get() = dataStore.getBoolean(ENABLED_KEY, true)
        set(isEnabled) = dataStore.put(ENABLED_KEY, isEnabled)

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getComponentGroup(): Int = AirshipComponentGroups.CHAT

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun init() {
        super.init()
        dataStore.addListener { key ->
            if (key == ENABLED_KEY) {
                updateConversationEnablement()
            }
        }

        updateConversationEnablement()
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onComponentEnableChange(isEnabled: Boolean) {
        super.onComponentEnableChange(isEnabled)
        updateConversationEnablement()
    }

    private fun updateConversationEnablement() {
        conversation.isEnabled = this.isEnabled && this.isComponentEnabled
    }
}
