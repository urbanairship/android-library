/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipExecutors
import com.urbanairship.Predicate
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Airship Message Center.
 *
 * @property inbox The inbox.
 */
public class MessageCenter
@VisibleForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    public val inbox: Inbox,
    private val pushManager: PushManager,
    dispatcher: CoroutineDispatcher
) : AirshipComponent(context, dataStore) {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + job)

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
        if (!privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER)) {
            return@PushListener
        }

        scope.launch {
            val hasMessageId = !message.richPushMessageId.isNullOrEmpty()
            val hasMessageData = inbox.getMessage(message.richPushMessageId) != null
            // If we have a message ID, but the message is not in the inbox, fetch messages.
            if (hasMessageId && !hasMessageData) {
                UALog.d("Received a Rich Push.")
                inbox.fetchMessages()
            }
        }
    }

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
        context = context,
        dataStore = dataStore,
        privacyManager = privacyManager,
        inbox = Inbox(context, dataStore, channel, config) { reason ->
            JobDispatcher.shared(context).scheduleInboxUpdateJob(reason)
        },
        pushManager = pushManager,
        dispatcher = Dispatchers.IO
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
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    public fun initialize() {
        pushManager.addInternalPushListener(pushListener)
        privacyManager.addListener {
            AirshipExecutors.newSerialExecutor().execute { updateInboxEnabledState() }
        }
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
    }

    /**
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onPerformJob(jobInfo: JobInfo): JobResult {
        return if (privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER)) {
            runBlocking {
                inbox.performUpdate().fold(onSuccess = { result ->
                    if (result) {
                        JobResult.SUCCESS
                    } else {
                        JobResult.RETRY
                    }
                }, onFailure = { JobResult.FAILURE })
            }
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
     * - Finally it will fallback to the provided [com.urbanairship.messagecenter.ui.MessageCenterActivity].
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

        // Fallback to the message center activity, if available
        try {
            val clazz = Class.forName("com.urbanairship.messagecenter.ui.MessageCenterActivity")
            intent.setClass(context, clazz)
            context.startActivity(intent)
        } catch (e: ClassNotFoundException) {
            UALog.w { "Unable to start MessageCenterActivity, the message-center module is not available"}
        }

    }

    override fun onAirshipDeepLink(uri: Uri): Boolean {
        if (DEEP_LINK_HOST == uri.encodedAuthority) {
            val paths = uri.pathSegments
            if (paths.isEmpty()) {
                showMessageCenter()
                return true
            } else if (paths.size == 1) {
                showMessageCenter(paths[0])
                return true
            } else if (paths.size == 2 && "message" == paths[0]) {
                showMessageCenter(paths[1])
                return true
            }
        }
        return false
    }

    public companion object {

        /**
         * Job action to update the inbox.
         */
        internal const val ACTION_UPDATE_INBOX = "ACTION_UPDATE_INBOX"

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
         * This method is the static entry point for Java clients. It delegates
         * access to the primary [Airship] singleton, ensuring the component is available
         * and fully initialized before returning.
         *
         * @return The MessageCenter instance.
         * @throws IllegalStateException if [Airship.takeOff] has not been called.
         *
         * @see Airship.messageCenter For the corresponding Kotlin extension property.
         */
        @JvmStatic
        public fun shared(): MessageCenter = Airship.messageCenter

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

internal fun JobDispatcher.scheduleInboxUpdateJob(reason: Inbox.UpdateType) {
    val jobInfo = JobInfo.newBuilder()
        .setAction(MessageCenter.ACTION_UPDATE_INBOX)
        .setAirshipComponent(MessageCenter::class.java)
        .let {
            when(reason) {
                Inbox.UpdateType.BEST_ATTEMPT ->
                    it.setNetworkAccessRequired(true)
                        .setConflictStrategy(JobInfo.ConflictStrategy.KEEP)

                Inbox.UpdateType.REQUIRED ->
                    it.setConflictStrategy(JobInfo.ConflictStrategy.REPLACE)
            }
        }
        .build()
    this.dispatch(jobInfo)
}


/**
 * Provides access to the [MessageCenter] module features via the main [Airship] singleton.
 *
 *
 * Access is thread-safe. Calling this property before Airship is finished taking off
 * will block the calling thread until initialization is complete.
 *
 * @return The MessageCenter instance.
 * @throws IllegalStateException if [Airship.takeOff] has not been called.
 *
 * @see MessageCenter.shared For the corresponding Java static access pattern.
 */
public val Airship.messageCenter: MessageCenter
    get() {
        return Airship.requireComponent(MessageCenter::class.java)
    }
