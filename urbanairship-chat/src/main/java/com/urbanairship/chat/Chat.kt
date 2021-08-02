/* Copyright Airship and Contributors */

package com.urbanairship.chat

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.Logger
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.chat.ui.ChatActivity
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.push.PushManager
import kotlinx.coroutines.runBlocking

/**
 * Airship Chat.
 */
class Chat

/**
 * Full constructor (for tests).
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) @VisibleForTesting internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val pushManager: PushManager,
    val conversation: Conversation,
    private val jobDispatcher: JobDispatcher = JobDispatcher.shared(context)
) : AirshipComponent(context, dataStore) {

    companion object {
        private const val REFRESH_MESSAGES_ACTION = "REFRESH_MESSAGES_ACTION"
        private const val REFRESH_MESSAGE_PUSH_KEY = "com.urbanairship.refresh_chat"
        private const val DEEP_LINK_HOST = "chat"
        private const val DEEP_LINK_INPUT_KEY = "chat_input"
        private const val DEEP_LINK_ROUTING_KEY = "routing"

        /**
         * Gets the shared `AirshipChat` instance.
         *
         * @return an instance of `AirshipChat`.
         */
        @JvmStatic
        fun shared(): Chat {
            return UAirship.shared().requireComponent(Chat::class.java)
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
        privacyManager: PrivacyManager,
        channel: AirshipChannel,
        pushManager: PushManager
    ) : this(context, dataStore, privacyManager, pushManager, Conversation(context, dataStore, config, channel))

    /**
     * Chat open listener.
     */
    var openChatListener: OnShowChatListener? = null

    /**
     * Enables or disables Chat.
     *
     * The value is persisted in shared preferences.
     */
    @Deprecated("Enable/disable by enabling {@link PrivacyManager#FEATURE_CHAT} in {@link PrivacyManager}. This will call through to the privacy manager.")
    var isEnabled: Boolean
        get() = privacyManager.isEnabled(PrivacyManager.FEATURE_CHAT)
        set(isEnabled) {
            if (isEnabled) {
                privacyManager.enable(PrivacyManager.FEATURE_CHAT)
            } else {
                privacyManager.disable(PrivacyManager.FEATURE_CHAT)
            }
        }

    /**
     * Opens the chat.
     * @param message The pre-filled chat message.
     * @param title The toolbar title.
     */
    @JvmOverloads
    fun openChat(message: String? = null, title: String? = null) {
        if (openChatListener?.onOpenChat(message) != true) {
            context.startActivity(
                    Intent(context, ChatActivity::class.java).apply {
                        message?.let { putExtra(ChatActivity.EXTRA_DRAFT, it) }
                        title?.let { putExtra(ChatActivity.EXTRA_TITLE, it) }
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
        privacyManager.addListener {
            updateConversationEnablement()
        }

        pushManager.addPushListener { message, _ ->
            if (message.containsKey(REFRESH_MESSAGE_PUSH_KEY)) {
                val jobInfo = JobInfo.newBuilder()
                        .setAction(REFRESH_MESSAGES_ACTION)
                        .setAirshipComponent(Chat::class.java)
                        .build()

                jobDispatcher.dispatch(jobInfo)
            }
        }

        updateConversationEnablement()
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onComponentEnableChange(isEnabled: Boolean) = updateConversationEnablement()

    private fun updateConversationEnablement() {
        conversation.isEnabled = this.isComponentEnabled && this.privacyManager.isEnabled(PrivacyManager.FEATURE_CHAT)
        if (!this.privacyManager.isEnabled(PrivacyManager.FEATURE_CHAT)) {
            conversation.clearData()
        }
    }

    override fun onUrlConfigUpdated() {
        conversation.launchConnectionUpdate()
    }

    override fun onPerformJob(airship: UAirship, jobInfo: JobInfo): Int {
        if (jobInfo.action == REFRESH_MESSAGES_ACTION) {
            val result = runBlocking {
                conversation.refreshMessages()
            }
            return if (result) {
                JobInfo.JOB_FINISHED
            } else {
                JobInfo.JOB_RETRY
            }
        } else {
            Logger.error("Unexpected job $jobInfo")
            return JobInfo.JOB_FINISHED
        }
    }

    override fun onAirshipDeepLink(uri: Uri): Boolean {
        return if (DEEP_LINK_HOST == uri.encodedAuthority && uri.pathSegments.size == 0) {
            val chatInput = uri.getQueryParameter(DEEP_LINK_INPUT_KEY)
            uri.getQueryParameter(DEEP_LINK_ROUTING_KEY)?.let {
                try {
                    conversation.routing = ChatRouting.fromJsonMap(JsonValue.parseString(it).optMap())
                } catch (e: JsonException) {
                    Logger.error("Failed to parse routing", e)
                }
            }
            openChat(chatInput)
            true
        } else {
            false
        }
    }
}
