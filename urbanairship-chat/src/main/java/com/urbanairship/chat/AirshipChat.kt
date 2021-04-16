/* Copyright Airship and Contributors */

package com.urbanairship.chat

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.chat.ui.ChatActivity
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
     * Listener to override open chat behavior.
     */
    interface OnShowChatListener {

        /**
         * Called when chat should be opened.
         * @param message Optional message to prefill the chat input box.
         * @return true if the chat was shown, otherwise false to trigger the default behavior.
         */
        fun onOpenChat(message: String?): Boolean
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
     * Chat open listener.
     */
    var openChatListener: OnShowChatListener? = null

    /**
     * Enables or disables Airship Chat.
     *
     * The value is persisted in shared preferences.
     */
    var isEnabled: Boolean
        get() = dataStore.getBoolean(ENABLED_KEY, true)
        set(isEnabled) = dataStore.put(ENABLED_KEY, isEnabled)

    /**
     * Opens the chat.
     * @param message The pre-filled chat message.
     */
    @JvmOverloads
    fun openChat(message: String? = null) {
        if (openChatListener?.onOpenChat(message) != true) {
            context.startActivity(
                    Intent(context, ChatActivity::class.java).apply {
                        message?.let { putExtra(ChatActivity.EXTRA_DRAFT, it) }
                        addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP)
                    }
            )
        }
    }

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
    override fun onComponentEnableChange(isEnabled: Boolean) = updateConversationEnablement()

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onDataCollectionEnabledChanged(isDataCollectionEnabled: Boolean) = updateConversationEnablement()

    private fun updateConversationEnablement() {
        conversation.isEnabled = this.isEnabled && this.isComponentEnabled && this.isDataCollectionEnabled
        if (!isDataCollectionEnabled) {
            conversation.clearData()
        }
    }

    override fun onUrlConfigUpdated() {
        conversation.updateConnection()
    }
}
