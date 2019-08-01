/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.AirshipComponent;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.richpush.RichPushInbox;

/**
 * Primary interface for configuring the default
 * Message Center implementation.
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

    private RichPushInbox.Predicate predicate;
    private OnShowMessageCenterListener onShowMessageCenterListener;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param dataStore The preference data store.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public MessageCenter(@NonNull Context context, @NonNull PreferenceDataStore dataStore) {
        super(context, dataStore);
    }

    /**
     * Returns the default inbox predicate.
     *
     * @return The default inbox predicate.
     */
    @Nullable
    public RichPushInbox.Predicate getPredicate() {
        return predicate;
    }

    /**
     * Sets the default inbox predicate.
     *
     * @param predicate The default inbox predicate.
     */
    public void setPredicate(@Nullable RichPushInbox.Predicate predicate) {
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

}
