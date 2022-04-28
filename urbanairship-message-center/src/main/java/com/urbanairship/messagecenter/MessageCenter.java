/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import static com.urbanairship.PrivacyManager.FEATURE_MESSAGE_CENTER;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.Predicate;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.push.PushListener;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Airship Message Center.
 */
public class MessageCenter extends AirshipComponent {

    /**
     * Intent action to view the message center.
     */
    @NonNull
    public static final String VIEW_MESSAGE_CENTER_INTENT_ACTION = "com.urbanairship.VIEW_RICH_PUSH_INBOX";

    /**
     * Intent action to view a message.
     */
    @NonNull
    public static final String VIEW_MESSAGE_INTENT_ACTION = "com.urbanairship.VIEW_RICH_PUSH_MESSAGE";

    /**
     * Scheme used for @{code message:<MESSAGE_ID>} when requesting to view a message with
     * {@code com.urbanairship.VIEW_RICH_PUSH_MESSAGE}.
     */
    @NonNull
    public static final String MESSAGE_DATA_SCHEME = "message";

    @NonNull
    private static final String DEEP_LINK_HOST = "message_center";

    private Predicate<Message> predicate;


    /**
     * Listener for showing the message center. If set, the listener
     * will be called to show the message center instead of the default behavior. For more
     * info see {@link #showMessageCenter()}.
     */
    public interface OnShowMessageCenterListener {

        /**
         * Called when the message center needs to be displayed.
         *
         * @param messageId The optional message Id.
         * @return {@code true} if the inbox was shown, otherwise {@code false} to trigger the default behavior.
         */
        boolean onShowMessageCenter(@Nullable String messageId);

    }

    private final PrivacyManager privacyManager;
    private final PushManager pushManager;
    private final Inbox inbox;
    private OnShowMessageCenterListener onShowMessageCenterListener;
    private final PushListener pushListener;

    private AtomicBoolean isStarted = new AtomicBoolean(false);

    /**
     * Gets the shared Message Center instance.
     *
     * @return The shared Message Center instance.
     */
    @NonNull
    public static MessageCenter shared() {
        return UAirship.shared().requireComponent(MessageCenter.class);
    }

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param dataStore The preference data store.
     * @param pushManager The push manager.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public MessageCenter(@NonNull Context context,
                         @NonNull PreferenceDataStore dataStore,
                         @NonNull PrivacyManager privacyManager,
                         @NonNull AirshipChannel channel,
                         @NonNull PushManager pushManager,
                         @NonNull AirshipConfigOptions configOptions) {
        this(context, dataStore, privacyManager, new Inbox(context, dataStore, channel, configOptions), pushManager);
    }

    /**
     * Constructor for testing.
     *
     * @param context The application context.
     * @param dataStore The preference data store.
     * @param inbox The inbox.
     * @param pushManager The push manager.
     * @hide
     */
    @VisibleForTesting
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    MessageCenter(@NonNull Context context,
                  @NonNull PreferenceDataStore dataStore,
                  @NonNull PrivacyManager privacyManager,
                  @NonNull Inbox inbox,
                  @NonNull PushManager pushManager) {
        super(context, dataStore);
        this.privacyManager = privacyManager;
        this.pushManager = pushManager;
        this.inbox = inbox;

        this.pushListener = new PushListener() {
            @WorkerThread
            @Override
            public void onPushReceived(@NonNull PushMessage message, boolean notificationPosted) {
                if (!UAStringUtil.isEmpty(message.getRichPushMessageId()) && getInbox().getMessage(message.getRichPushMessageId()) == null) {
                    Logger.debug("Received a Rich Push.");
                    getInbox().fetchMessages();
                }
            }
        };
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void init() {
        super.init();

        privacyManager.addListener(new PrivacyManager.Listener() {
            @Override
            public void onEnabledFeaturesChanged() {
                AirshipExecutors.newSerialExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        updateInboxEnabledState();
                    }
                });
            }
        });

        updateInboxEnabledState();
    }

    /**
     * Update the enabled state of the Inbox and initialize it if necessary.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void updateInboxEnabledState() {
        boolean isEnabled = privacyManager.isEnabled(FEATURE_MESSAGE_CENTER);

        inbox.setEnabled(isEnabled);
        inbox.updateEnabledState();

        if (isEnabled) {
            if (!isStarted.getAndSet(true)) {
                Logger.verbose("Initializing Inbox...");

                pushManager.addInternalPushListener(pushListener);
            }
        } else {
            tearDown();
        }
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.MESSAGE_CENTER;
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onUrlConfigUpdated() {
        // Update inbox user when notified of new URL config.
        getInbox().dispatchUpdateUserJob(true);
    }

    /**
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public JobResult onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        if (privacyManager.isEnabled(FEATURE_MESSAGE_CENTER)) {
            return inbox.onPerformJob(airship, jobInfo);
        } else {
            return JobResult.SUCCESS;
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void tearDown() {
        inbox.tearDown();
        pushManager.removePushListener(pushListener);
        isStarted.set(false);
    }

    /**
     * Returns the inbox.
     *
     * @return The inbox.
     */
    @NonNull
    public Inbox getInbox() {
        return inbox;
    }

    /**
     * Returns the inbox user.
     *
     * @return The inbox user.
     */
    @NonNull
    public User getUser() {
        return inbox.getUser();
    }

    /**
     * Returns the default inbox predicate.
     *
     * @return The default inbox predicate.
     */
    @Nullable
    public Predicate<Message> getPredicate() {
        return predicate;
    }

    /**
     * Sets the default inbox predicate.
     *
     * @param predicate The default inbox predicate.
     */
    public void setPredicate(@Nullable Predicate<Message> predicate) {
        this.predicate = predicate;
    }

    /**
     * Sets the show message center listener.
     *
     * @param listener The listener.
     */
    public void setOnShowMessageCenterListener(@Nullable OnShowMessageCenterListener listener) {
        this.onShowMessageCenterListener = listener;
    }

    /**
     * Called to show the message center. See {@link #showMessageCenter(String)} for more details.
     */
    public void showMessageCenter() {
        showMessageCenter(null);
    }

    /**
     * Called to show the message center for a specific message.
     *
     * To show the message center, the SDK will try the following:
     * <pre>
     * - The optional {@link OnShowMessageCenterListener}.
     * - An implicit intent with {@code com.urbanairship.VIEW_RICH_PUSH_INBOX}.
     * - If the message ID is provided, an implicit intent with {@code com.urbanairship.VIEW_MESSAGE_INTENT_ACTION}.
     * - Finally it will fallback to the provided {@link MessageCenterActivity}.
     * </pre>
     *
     * Implicit intents will have the message ID encoded as the intent's data with the format @{code message:<MESSAGE_ID>}.
     *
     * @param messageId The message ID.
     */
    public void showMessageCenter(@Nullable String messageId) {
        if (!privacyManager.isEnabled(FEATURE_MESSAGE_CENTER)) {
            Logger.warn("Unable to show Message Center. FEATURE_MESSAGE_CENTER is not enabled in PrivacyManager.");
            return;
        }

        // Try the listener
        OnShowMessageCenterListener listener = this.onShowMessageCenterListener;
        if (listener != null && listener.onShowMessageCenter(messageId)) {
            return;
        }

        // Try the VIEW_MESSAGE_CENTER_INTENT_ACTION intent
        Intent intent = new Intent(VIEW_MESSAGE_CENTER_INTENT_ACTION)
                .setPackage(getContext().getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (messageId != null) {
            intent.setData(Uri.fromParts(MESSAGE_DATA_SCHEME, messageId, null));
        }

        if (intent.resolveActivity(getContext().getPackageManager()) != null) {
            getContext().startActivity(intent);
            return;
        }

        // Try the VIEW_MESSAGE_INTENT_ACTION if the message ID is available
        if (messageId != null) {
            intent.setAction(VIEW_MESSAGE_INTENT_ACTION);
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
                return;
            }
        }

        // Fallback to the message center activity
        intent.setClass(getContext(), MessageCenterActivity.class);
        getContext().startActivity(intent);
    }

    /**
     * Parses the message Id from a message center intent.
     *
     * @param intent The intent.
     * @return The message Id if it's available on the intent, otherwise {@code null}.
     */
    @Nullable
    public static String parseMessageId(@Nullable Intent intent) {
        if (intent == null || intent.getData() == null || intent.getAction() == null) {
            return null;
        }

        if (!MESSAGE_DATA_SCHEME.equalsIgnoreCase(intent.getData().getScheme())) {
            return null;
        }

        switch (intent.getAction()) {
            case VIEW_MESSAGE_CENTER_INTENT_ACTION:
            case VIEW_MESSAGE_INTENT_ACTION:
                return intent.getData().getSchemeSpecificPart();

            default:
                return null;
        }
    }

    @Override
    public boolean onAirshipDeepLink(@NonNull Uri uri) {
        if (DEEP_LINK_HOST.equals(uri.getEncodedAuthority())) {
            List<String> paths = uri.getPathSegments();
            if (paths.size() == 0) {
                showMessageCenter();
                return true;
            } else if (paths.size() == 1) {
                showMessageCenter(paths.get(0));
                return true;
            }
        }

        return false;
    }

}
