package com.urbanairship.iam;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.Trigger;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.push.NotificationProxyReceiver;
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

    /**
     * Default primary color.
     */
    public static final int DEFAULT_PRIMARY_COLOR = Color.WHITE;

    /**
     * Default secondary color.
     */
    public static final int DEFAULT_SECONDARY_COLOR = Color.BLACK;

    /**
     * Default border radius.
     */
    public static final float DEFAULT_BORDER_RADIUS_DP = 2;

    private final InAppMessageManager inAppMessageManager;
    private final PreferenceDataStore preferenceDataStore;
    private final Analytics analytics;
    private MessageBuilderExtender messageBuilderExtender;
    private ScheduleInfoBuilderExtender scheduleBuilderExtender;
    private boolean displayAsapEnabled = true;

    /**
     * Interface to extend the {@link InAppMessage.Builder} that generates the message from a
     * legacy in-app message.
     */
    public interface MessageBuilderExtender {

        /**
         * Extends the {@link InAppMessage.Builder}.
         *
         * @param context The application context.
         * @param legacyMessage The legacy in-app message.
         * @return The builder.
         */
        @NonNull
        InAppMessage.Builder extend(@NonNull Context context, @NonNull InAppMessage.Builder builder, @NonNull LegacyInAppMessage legacyMessage);

    }

    /**
     * Interface to extend the {@link InAppMessageScheduleInfo.Builder} that generates the in-app
     * schedule info from a legacy in-app message.
     */
    public interface ScheduleInfoBuilderExtender {

        /**
         * Extends the {@link InAppMessageScheduleInfo.Builder}.
         *
         * @param context The application context.
         * @param legacyMessage The legacy in-app message.
         * @return The builder.
         */
        @NonNull
        InAppMessageScheduleInfo.Builder extend(@NonNull Context context, @NonNull InAppMessageScheduleInfo.Builder builder, @NonNull LegacyInAppMessage legacyMessage);

    }

    /**
     * Default constructor.
     *
     * @param preferenceDataStore The preference data store.
     * @param inAppMessageManager The in-app message manager.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public LegacyInAppMessageManager(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore, @NonNull InAppMessageManager inAppMessageManager, @NonNull Analytics analytics) {
        super(context, preferenceDataStore);
        this.preferenceDataStore = preferenceDataStore;
        this.inAppMessageManager = inAppMessageManager;
        this.analytics = analytics;
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
     * Sets the in-app message builder extender.
     *
     * @param messageBuilderExtender The extender.
     */
    public void setMessageBuilderExtender(@Nullable MessageBuilderExtender messageBuilderExtender) {
        this.messageBuilderExtender = messageBuilderExtender;
    }

    /**
     * Sets the in-app schedule info builder extender.
     *
     * @param scheduleBuilderExtender The extender.
     */
    public void setScheduleBuilderExtender(@Nullable ScheduleInfoBuilderExtender scheduleBuilderExtender) {
        this.scheduleBuilderExtender = scheduleBuilderExtender;
    }

    /**
     * Sets whether legacy messages will display immediately upon arrival, instead of waiting
     * until the following foreground. Defaults to <code>true</code>.
     *
     * @param enabled Whether immediate display is enabled.
     */
    public void setDisplayAsapEnabled(boolean enabled) {
        displayAsapEnabled = enabled;
    }

    /**
     * Determines whether legacy messages will display immediately upon arrival, instead of waiting
     * until the following foreground.
     *
     * @return <code>true</code> if immediate display is enabled, otherwise <code>false</code>.
     */
    public boolean getDisplayAsapEnabled() {
        return displayAsapEnabled;
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
            Logger.error(e, "LegacyInAppMessageManager - Unable to create in-app message from push payload");
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
     * Called when a push is interacted with in {@link NotificationProxyReceiver}.
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
                    Logger.debug("Clearing pending in-app message due to directly interacting with the message's push notification.");
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
    @Nullable
    private InAppMessageScheduleInfo createScheduleInfo(@NonNull Context context, @NonNull LegacyInAppMessage legacyInAppMessage) {
        try {
            Trigger trigger;

            // In terms of the scheduled message model, displayAsap means using an active session trigger.
            // Otherwise the closest analog to the v1 behavior is the foreground trigger.
            if (displayAsapEnabled) {
                trigger = Triggers.newActiveSessionTriggerBuilder().build();
            } else {
                trigger = Triggers.newForegroundTriggerBuilder().build();
            }

            InAppMessageScheduleInfo.Builder builder = InAppMessageScheduleInfo.newBuilder()
                                                                               .addTrigger(trigger)
                                                                               .setEnd(legacyInAppMessage.getExpiry());

            ScheduleInfoBuilderExtender builderExtender = this.scheduleBuilderExtender;
            if (builderExtender != null) {
                builderExtender.extend(context, builder, legacyInAppMessage);
            }

            return builder.setMessage(createMessage(context, legacyInAppMessage))
                          .build();

        } catch (Exception e) {
            Logger.error(e, "Error during factory method to convert legacy in-app message.");
            return null;
        }
    }

    /**
     * Creates the in-app message.
     *
     * @param context The application context.
     * @param legacyMessage The legacy in-app message.
     * @return The In-App message.
     */
    @NonNull
    private InAppMessage createMessage(@NonNull Context context, @NonNull LegacyInAppMessage legacyMessage) {
        @ColorInt
        int primaryColor = legacyMessage.getPrimaryColor() == null ? DEFAULT_PRIMARY_COLOR : legacyMessage.getPrimaryColor();

        @ColorInt
        int secondaryColor = legacyMessage.getSecondaryColor() == null ? DEFAULT_SECONDARY_COLOR : legacyMessage.getSecondaryColor();

        BannerDisplayContent.Builder displayContentBuilder = BannerDisplayContent.newBuilder()
                                                                                 .setBackgroundColor(primaryColor)
                                                                                 .setDismissButtonColor(secondaryColor)
                                                                                 .setBorderRadius(DEFAULT_BORDER_RADIUS_DP)
                                                                                 .setButtonLayout(DisplayContent.BUTTON_LAYOUT_SEPARATE)
                                                                                 .setPlacement(legacyMessage.getPlacement())
                                                                                 .setActions(legacyMessage.getClickActionValues())
                                                                                 .setBody(TextInfo.newBuilder()
                                                                                                  .setText(legacyMessage.getAlert())
                                                                                                  .setColor(secondaryColor)
                                                                                                  .build());

        if (legacyMessage.getDuration() != null) {
            displayContentBuilder.setDuration(legacyMessage.getDuration(), TimeUnit.MILLISECONDS);
        }

        // Buttons
        if (legacyMessage.getButtonGroupId() != null) {
            NotificationActionButtonGroup group = UAirship.shared().getPushManager().getNotificationActionGroup(legacyMessage.getButtonGroupId());

            if (group != null) {
                for (int i = 0; i < group.getNotificationActionButtons().size(); i++) {
                    if (i >= BannerDisplayContent.MAX_BUTTONS) {
                        break;
                    }

                    NotificationActionButton button = group.getNotificationActionButtons().get(i);

                    TextInfo.Builder labelBuilder = TextInfo.newBuilder()
                                                            .setDrawable(context, button.getIcon())
                                                            .setColor(primaryColor)
                                                            .setAlignment(TextInfo.ALIGNMENT_CENTER)
                                                            .setText(button.getLabel(context));

                    ButtonInfo.Builder buttonInfoBuilder = ButtonInfo.newBuilder()
                                                                     .setActions(legacyMessage.getButtonActionValues(button.getId()))
                                                                     .setId(button.getId())
                                                                     .setBackgroundColor(secondaryColor)
                                                                     .setBorderRadius(DEFAULT_BORDER_RADIUS_DP)
                                                                     .setLabel(labelBuilder.build());

                    displayContentBuilder.addButton(buttonInfoBuilder.build());
                }
            }
        }

        InAppMessage.Builder builder = InAppMessage.newBuilder()
                                                   .setDisplayContent(displayContentBuilder.build())
                                                   .setExtras(legacyMessage.getExtras());

        MessageBuilderExtender builderExtender = this.messageBuilderExtender;
        if (builderExtender != null) {
            builderExtender.extend(context, builder, legacyMessage);
        }

        // Set ID and source after building the message
        return builder.setSource(InAppMessage.SOURCE_LEGACY_PUSH)
                      .setId(legacyMessage.getId())
                      .build();
    }

}
