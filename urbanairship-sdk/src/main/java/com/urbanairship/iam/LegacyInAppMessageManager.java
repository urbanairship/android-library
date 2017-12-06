package com.urbanairship.iam;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import java.util.concurrent.TimeUnit;

/**
 * Legacy in-app message manager.
 */
public class LegacyInAppMessageManager extends AirshipComponent {

    // Preference data store keys
    private final static String KEY_PREFIX = "com.urbanairship.push.iam.";

    // OLD Storage Keys
    private final static String PENDING_IN_APP_MESSAGE_KEY = KEY_PREFIX + "PENDING_IN_APP_MESSAGE";
    private final static String AUTO_DISPLAY_ENABLED_KEY = KEY_PREFIX + "AUTO_DISPLAY_ENABLED";
    private final static String LAST_DISPLAYED_ID_KEY = KEY_PREFIX + "LAST_DISPLAYED_ID";

    // New ID key
    private final static String PENDING_MESSAGE_ID = KEY_PREFIX + "PENDING_MESSAGE_ID";

    public static final int DEFAULT_PRIMARY_COLOR = Color.BLACK;
    public static final int DEFAULT_SECONDARY_COLOR = Color.WHITE;


    private final InAppMessageManager inAppMessageManager;
    private final PreferenceDataStore preferenceDataStore;
    private final Analytics analytics;
    private Factory factory;

    /**
     * Factory to generate an {@link InAppMessageScheduleInfo} from a {@link LegacyInAppMessage}.
     */
    interface Factory {

        /**
         * Creates a {@link InAppMessageScheduleInfo} from a {@link LegacyInAppMessage}.
         *
         * @param context The application context.
         * @param inAppMessage The legacy in-app message.
         * @return An in-app message schedule info.
         */
        @Nullable
        InAppMessageScheduleInfo createScheduleInfo(Context context, LegacyInAppMessage inAppMessage);

    }

    /**
     * Default constructor.
     *
     * @param preferenceDataStore The preference data store.
     * @param inAppMessageManager The in-app message manager.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public LegacyInAppMessageManager(PreferenceDataStore preferenceDataStore, InAppMessageManager inAppMessageManager, Analytics analytics) {
        super(preferenceDataStore);
        this.preferenceDataStore = preferenceDataStore;
        this.inAppMessageManager = inAppMessageManager;
        this.analytics = analytics;
        this.factory = new DefaultFactory();
    }

    @Override
    protected void init() {
        super.init();

        // Clean up the old store
        preferenceDataStore.remove(PENDING_IN_APP_MESSAGE_KEY);
        preferenceDataStore.remove(AUTO_DISPLAY_ENABLED_KEY);
        preferenceDataStore.remove(LAST_DISPLAYED_ID_KEY);
    }

    /**
     * Sets the factory that generates the {@link InAppMessageScheduleInfo} from
     * a {@link LegacyInAppMessage}.
     *
     * @param factory The factory.
     */
    public void setFactory(Factory factory) {
        this.factory = factory;
    }

    /**
     * Called when a push is received in {@link com.urbanairship.push.IncomingPushRunnable}.
     *
     * @param push The push message.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onPushReceived(@NonNull final PushMessage push) {
        LegacyInAppMessage legacyInAppMessage = null;

        try {
            legacyInAppMessage = LegacyInAppMessage.fromPush(push);
        } catch (IllegalArgumentException | JsonException e) {
            Logger.error("LegacyInAppMessageManager - Unable to create in-app message from push payload", e);
        }

        if (legacyInAppMessage == null) {
            return;
        }

        InAppMessageScheduleInfo scheduleInfo = createScheduleInfo(UAirship.getApplicationContext(), legacyInAppMessage);
        if (scheduleInfo == null) {
            return;
        }

        final String messageId = scheduleInfo.getInAppMessage().getId();

        Logger.debug("LegacyInAppMessageManager - Received a Push with an in-app message.");

        final String pendingMessageId = preferenceDataStore.getString(PENDING_MESSAGE_ID, null);

        // Cancel the previous pending message if its still scheduled
        if (pendingMessageId != null) {
            inAppMessageManager.cancelMessage(pendingMessageId).addResultCallback(new ResultCallback<Boolean>() {
                @Override
                public void onResult(@Nullable Boolean result) {
                    if (result != null && result) {
                        Logger.debug("LegacyInAppMessageManager - Pending in-app message replaced.");
                        ResolutionEvent resolutionEvent = ResolutionEvent.legacyMessageReplaced(pendingMessageId, messageId);
                        analytics.addEvent(resolutionEvent);
                    }
                }
            });
        }

        // Schedule the new one
        inAppMessageManager.scheduleMessage(scheduleInfo);

        // Store the pending ID
        preferenceDataStore.put(PENDING_MESSAGE_ID, messageId);
    }

    /**
     * Called when a push is interacted with in {@link com.urbanairship.CoreReceiver}.
     *
     * @param push The push message.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onPushResponse(@NonNull final PushMessage push) {
        if (push.getSendId() == null || !push.containsKey(PushMessage.EXTRA_IN_APP_MESSAGE)) {
            return;
        }

        inAppMessageManager.cancelMessage(push.getSendId()).addResultCallback(new ResultCallback<Boolean>() {
            @Override
            public void onResult(@Nullable Boolean result) {
                if (result != null && result) {
                    Logger.info("Clearing pending in-app message due to directly interacting with the message's push notification.");
                    // Direct open event
                    ResolutionEvent resolutionEvent = ResolutionEvent.legacyMessagePushOpened(push.getSendId());
                    analytics.addEvent(resolutionEvent);
                }
            }
        });
    }

    /**
     * Creates a schedule info from the legacy in-app message.
     *
     * @param context The application context.
     * @param legacyInAppMessage The legacy in-app message.
     * @return The schedule info, or {@code null} if the factory is unable to create a schedule info.
     */
    private InAppMessageScheduleInfo createScheduleInfo(Context context, LegacyInAppMessage legacyInAppMessage) {
        Factory factory = this.factory;
        if (factory == null) {
            Logger.error("Unable to convert legacy in-app message. Missing factory.");
            return null;
        }

        InAppMessageScheduleInfo scheduleInfo;
        try {
            scheduleInfo = factory.createScheduleInfo(context, legacyInAppMessage);
        } catch (Exception e) {
            Logger.error("Error during factory method to convert legacy in-app message.", e);
            return null;
        }

        if (scheduleInfo == null) {
            Logger.error("Failed to convert legacy in-app message.");
            return null;
        }

        if (!legacyInAppMessage.getId().equals(scheduleInfo.getInAppMessage().getId())) {
            Logger.error("Legacy in-app message ID does not match generated message.");
        }

        return scheduleInfo;
    }

    /**
     * The default {@link Factory}.
     */
    public static class DefaultFactory implements Factory {

        private static final float DEFAULT_BORDER_RADIUS_DP = 2;

        @Override
        @Nullable
        public InAppMessageScheduleInfo createScheduleInfo(Context context, LegacyInAppMessage inAppMessage) {
            @ColorInt
            int primaryColor = inAppMessage.getPrimaryColor() == null ? DEFAULT_PRIMARY_COLOR : inAppMessage.getPrimaryColor();

            @ColorInt
            int secondaryColor = inAppMessage.getSecondaryColor() == null ? DEFAULT_SECONDARY_COLOR : inAppMessage.getSecondaryColor();

            BannerDisplayContent.Builder displayContentBuilder = BannerDisplayContent.newBuilder()
                                                                                     .setBackgroundColor(secondaryColor)
                                                                                     .setDismissButtonColor(primaryColor)
                                                                                     .setBorderRadius(DEFAULT_BORDER_RADIUS_DP)
                                                                                     .setButtonLayout(DisplayContent.BUTTON_LAYOUT_SEPARATE)
                                                                                     .setPlacement(inAppMessage.getPlacement())
                                                                                     .setActions(inAppMessage.getClickActionValues())
                                                                                     .setBody(TextInfo.newBuilder()
                                                                                                      .setText(inAppMessage.getAlert())
                                                                                                      .setColor(primaryColor)
                                                                                                      .build());

            if (inAppMessage.getDuration() != null) {
                displayContentBuilder.setDuration(inAppMessage.getDuration(), TimeUnit.MILLISECONDS);
            }

            // Buttons
            if (inAppMessage.getButtonGroupId() != null) {
                NotificationActionButtonGroup group = UAirship.shared().getPushManager().getNotificationActionGroup(inAppMessage.getButtonGroupId());

                if (group != null) {
                    for (int i = 0; i < group.getNotificationActionButtons().size(); i++) {
                        if (i >= BannerDisplayContent.MAX_BUTTONS) {
                            break;
                        }

                        NotificationActionButton button = group.getNotificationActionButtons().get(i);

                        TextInfo.Builder labelBuilder = TextInfo.newBuilder()
                                                                .setDrawable(button.getIcon())
                                                                .setColor(secondaryColor)
                                                                .setAlignment(TextInfo.ALIGNMENT_CENTER);

                        if (button.getLabel() != 0) {
                            labelBuilder.setText(context.getString(button.getLabel()));
                        }

                        ButtonInfo.Builder buttonInfoBuilder = ButtonInfo.newBuilder()
                                                                         .setActions(inAppMessage.getButtonActionValues(button.getId()))
                                                                         .setId(button.getId())
                                                                         .setBackgroundColor(primaryColor)
                                                                         .setBorderRadius(DEFAULT_BORDER_RADIUS_DP)
                                                                         .setLabel(labelBuilder.build());

                        displayContentBuilder.addButton(buttonInfoBuilder.build());
                    }
                }
            }

            return InAppMessageScheduleInfo.newBuilder()
                                           .addTrigger(Triggers.newActiveSessionTriggerBuilder().build())
                                           .setEnd(inAppMessage.getExpiry())
                                           .setMessage(InAppMessage.newBuilder()
                                                                   .setDisplayContent(displayContentBuilder.build())
                                                                   .setExtras(inAppMessage.getExtras())
                                                                   .setId(inAppMessage.getId())
                                                                   .build())
                                           .build();
        }
    }

}
