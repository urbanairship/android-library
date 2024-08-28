/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipExecutors
import com.urbanairship.Predicate
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Airship Message Center.
 *
 * @property inbox The inbox.
 */
public class MessageCenter
@VisibleForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val config: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    public val inbox: Inbox,
    private val pushManager: PushManager
) : AirshipComponent(context, dataStore) {

    /** The default inbox predicate. */
    public var predicate: Predicate<Message>? = null

    /**
     * Listener for showing the message center. If set, the listener
     * will be called to show the message center instead of the default behavior.
     *
     * For more info see: [showMessageCenter].
     */
    public fun interface OnShowMessageCenterListener {

        /**
         * Called when the message center needs to be displayed.
         *
         * @param messageId The optional message Id.
         * @return `true` if the inbox was shown, otherwise `false` to trigger the default behavior.
         */
        public fun onShowMessageCenter(messageId: String?): Boolean
    }

    private var onShowMessageCenterListener: OnShowMessageCenterListener? = null

    private val pushListener: PushListener = PushListener { message: PushMessage, _: Boolean ->
        val hasMessageId = !message.richPushMessageId.isNullOrEmpty()
        val isMessageMissing = inbox.getMessage(message.richPushMessageId) == null
        // If we have a message ID, but the message is not in the inbox, fetch messages.
        if (hasMessageId && isMessageMissing) {
            UALog.d("Received a Rich Push.")
            inbox.fetchMessages()
        }
    }

    private val isStarted = AtomicBoolean(false)

    /**
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
    ) : this(
        context,
        dataStore,
        config,
        privacyManager,
        Inbox(context, dataStore, channel, config.configOptions, privacyManager),
        pushManager
    )

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun init() {
        super.init()
        initialize()
    }

    /**
     * Internal version of [init], which is protected in the abstract [AirshipComponent].
     *
     * @hide
     */
    @VisibleForTesting
    internal fun initialize() {
        privacyManager.addListener {
            AirshipExecutors.newSerialExecutor().execute { updateInboxEnabledState() }
        }
        config.addConfigListener { inbox.dispatchUpdateUserJob(true) }
        updateInboxEnabledState()
    }

    /**
     * Update the enabled state of the Inbox and initialize it if necessary.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun updateInboxEnabledState() {
        val isEnabled = privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER)
        inbox.setEnabled(isEnabled)
        inbox.updateEnabledState()
        if (isEnabled) {
            if (!isStarted.getAndSet(true)) {
                UALog.v("Initializing Inbox...")
                pushManager.addInternalPushListener(pushListener)
            }
        } else {
            tearDown()
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    override fun getComponentGroup(): Int {
        return AirshipComponentGroups.MESSAGE_CENTER
    }

    /**
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onPerformJob(airship: UAirship, jobInfo: JobInfo): JobResult {
        return if (privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER)) {
            inbox.onPerformJob(airship, jobInfo)
        } else {
            JobResult.SUCCESS
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun tearDown() {
        inbox.tearDown()
        pushManager.removePushListener(pushListener)
        isStarted.set(false)
    }

    /** The inbox user. */
    public val user: User
        get() = inbox.user

    /**
     * Sets the show message center listener.
     *
     * @param listener The listener.
     */
    public fun setOnShowMessageCenterListener(listener: OnShowMessageCenterListener?) {
        onShowMessageCenterListener = listener
    }

    /**
     * Called to show the message center for a specific message.
     *
     * To show the message center, the SDK will try the following:
     * - The optional [OnShowMessageCenterListener].
     * - An implicit intent with `com.urbanairship.VIEW_RICH_PUSH_INBOX`.
     * - If the message ID is provided, an implicit intent with `com.urbanairship.VIEW_MESSAGE_INTENT_ACTION`.
     * - Finally it will fallback to the provided [MessageCenterActivity].
     *
     * Implicit intents will have the message ID encoded as the intent's data with the format `message:<MESSAGE_ID>`.
     *
     * @param messageId An optional message ID to display. If `null`, the inbox will be displayed.
     */
    @JvmOverloads
    public fun showMessageCenter(messageId: String? = null) {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER)) {
            UALog.w { "Unable to show Message Center. FEATURE_MESSAGE_CENTER is not enabled in PrivacyManager." }
            return
        }

        // Try the listener
        val listener = onShowMessageCenterListener
        if (listener != null && listener.onShowMessageCenter(messageId)) {
            return
        }

        // Try the VIEW_MESSAGE_CENTER_INTENT_ACTION intent
        val intent = Intent(VIEW_MESSAGE_CENTER_INTENT_ACTION)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        messageId?.let { intent.setData(Uri.fromParts(MESSAGE_DATA_SCHEME, it, null)) }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }

        // Try the VIEW_MESSAGE_INTENT_ACTION if the message ID is available
        if (messageId != null) {
            intent.setAction(VIEW_MESSAGE_INTENT_ACTION)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        }

        // Fallback to the message center activity
        intent.setClass(context, MessageCenterActivity::class.java)
        context.startActivity(intent)
    }

    override fun onAirshipDeepLink(uri: Uri): Boolean {
        if (DEEP_LINK_HOST == uri.encodedAuthority) {
            val paths = uri.pathSegments
            if (paths.size == 0) {
                showMessageCenter()
                return true
            } else if (paths.size == 1) {
                showMessageCenter(paths[0])
                return true
            }
        }
        return false
    }

    public companion object {

        /**
         * Intent action to view the message center.
         */
        public const val VIEW_MESSAGE_CENTER_INTENT_ACTION: String = "com.urbanairship.VIEW_RICH_PUSH_INBOX"

        /**
         * Intent action to view a message.
         */
        public const val VIEW_MESSAGE_INTENT_ACTION: String = "com.urbanairship.VIEW_RICH_PUSH_MESSAGE"

        /**
         * Scheme used for `message:<MESSAGE_ID>` when requesting to view a message with
         * `com.urbanairship.VIEW_RICH_PUSH_MESSAGE`.
         */
        public const val MESSAGE_DATA_SCHEME: String = "message"

        private const val DEEP_LINK_HOST = "message_center"

        /**
         * Gets the shared Message Center instance.
         *
         * @return The shared Message Center instance.
         */
        @JvmStatic
        public fun shared(): MessageCenter =
            UAirship.shared().requireComponent(MessageCenter::class.java)

        /**
         * Parses the message Id from a message center intent.
         *
         * @param intent The intent.
         * @return The message Id if it's available on the intent, otherwise `null`.
         */
        @JvmStatic
        public fun parseMessageId(intent: Intent?): String? {
            if (intent == null || intent.data == null || intent.action == null) {
                return null
            }
            return if (!MESSAGE_DATA_SCHEME.equals(intent.data?.scheme, ignoreCase = true)) {
                null
            } else when (intent.action) {
                VIEW_MESSAGE_CENTER_INTENT_ACTION,
                VIEW_MESSAGE_INTENT_ACTION -> intent.data?.schemeSpecificPart
                else -> null
            }
        }
    }
}
