package com.urbanairship.iam;

import android.content.Context;
import android.graphics.Color;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.automation.Schedule;
import com.urbanairship.automation.Trigger;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.json.JsonException;
import com.urbanairship.push.InternalNotificationListener;
import com.urbanairship.push.NotificationActionButtonInfo;
import com.urbanairship.push.NotificationInfo;
import com.urbanairship.push.PushListener;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import java.util.concurrent.TimeUnit;

import androidx.annotation.ColorInt;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

/**
 * Legacy in-app message manager.
 */
public class LegacyInAppMessageManager extends AirshipComponent {

    // New ID key
    private final static String PENDING_MESSAGE_ID =  "com.urbanairship.push.iam.PENDING_MESSAGE_ID";

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

    private final InAppAutomation inAppAutomation;
    private final PreferenceDataStore preferenceDataStore;
    private final Analytics analytics;
    private final PushManager pushManager;
    private MessageBuilderExtender messageBuilderExtender;
    private ScheduleBuilderExtender scheduleBuilderExtender;
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
     * Interface to extend the {@link Schedule.Builder} that generates the in-app
     * schedule info from a legacy in-app message.
     */
    public interface ScheduleBuilderExtender {

        /**
         * Extends the {@link Schedule.Builder}.
         *
         * @param context The application context.
         * @param legacyMessage The legacy in-app message.
         * @return The builder.
         */
        @NonNull
        Schedule.Builder<InAppMessage> extend(@NonNull Context context, @NonNull Schedule.Builder<InAppMessage> builder, @NonNull LegacyInAppMessage legacyMessage);

    }

    /**
     * Gets the shared Legacy In-App Message Manager instance.
     *
     * @return The shared Legacy In-App Message Manager instance.
     */
    @NonNull
    public static LegacyInAppMessageManager shared() {
        return UAirship.shared().requireComponent(LegacyInAppMessageManager.class);
    }

    /**
     * Default constructor.
     *
     * @param preferenceDataStore The preference data store.
     * @param inAppAutomation The in-app automation instance.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public LegacyInAppMessageManager(@NonNull Context context,
                                     @NonNull PreferenceDataStore preferenceDataStore,
                                     @NonNull InAppAutomation inAppAutomation,
                                     @NonNull Analytics analytics,
                                     @NonNull PushManager push) {
        super(context, preferenceDataStore);
        this.preferenceDataStore = preferenceDataStore;
        this.inAppAutomation = inAppAutomation;
        this.analytics = analytics;
        this.pushManager = push;
    }

    @Override
    protected void init() {
        super.init();

        pushManager.addInternalPushListener(new PushListener() {
            @Override
            @WorkerThread
            public void onPushReceived(@NonNull PushMessage message, boolean notificationPosted) {
                LegacyInAppMessage legacyInAppMessage = null;

                try {
                    legacyInAppMessage = LegacyInAppMessage.fromPush(message);
                } catch (IllegalArgumentException | JsonException e) {
                    Logger.error(e, "LegacyInAppMessageManager - Unable to create in-app message from push payload");
                }

                if (legacyInAppMessage == null) {
                    return;
                }

                Schedule<InAppMessage> schedule = createSchedule(UAirship.getApplicationContext(), legacyInAppMessage);
                if (schedule == null) {
                    return;
                }

                final String messageId = schedule.getId();

                Logger.debug("Received a Push with an in-app message.");

                final String pendingMessageId = preferenceDataStore.getString(PENDING_MESSAGE_ID, null);

                // Cancel the previous pending message if it's still scheduled
                if (pendingMessageId != null) {
                    inAppAutomation.cancelSchedule(pendingMessageId).addResultCallback(new ResultCallback<Boolean>() {
                        @Override
                        public void onResult(@Nullable Boolean result) {
                            if (result != null && result) {
                                Logger.debug("Pending in-app message replaced.");
                                InAppReportingEvent.legacyReplaced(pendingMessageId, messageId)
                                                   .record(analytics);
                            }
                        }
                    });
                }

                // Schedule the new one
                inAppAutomation.schedule(schedule);

                // Store the pending ID
                preferenceDataStore.put(PENDING_MESSAGE_ID, messageId);
            }
        });

        pushManager.addInternalNotificationListener(new InternalNotificationListener() {
            @Override
            @MainThread
            public void onNotificationResponse(@NonNull NotificationInfo notificationInfo, @Nullable NotificationActionButtonInfo actionButtonInfo) {
                final PushMessage push = notificationInfo.getMessage();
                if (push.getSendId() == null || !push.containsKey(PushMessage.EXTRA_IN_APP_MESSAGE)) {
                    return;
                }

                inAppAutomation.cancelSchedule(push.getSendId()).addResultCallback(new ResultCallback<Boolean>() {
                    @Override
                    public void onResult(@Nullable Boolean result) {
                        if (result != null && result) {
                            Logger.debug("Clearing pending in-app message due to directly interacting with the message's push notification.");
                            // Direct open event
                            InAppReportingEvent.legacyPushOpened(push.getSendId())
                                               .record(analytics);
                        }
                    }
                });
            }
        });
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.IN_APP;
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
    public void setScheduleBuilderExtender(@Nullable ScheduleBuilderExtender scheduleBuilderExtender) {
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
     * Creates a schedule info from the legacy in-app message.
     *
     * @param context The application context.
     * @param legacyInAppMessage The legacy in-app message.
     * @return The schedule info, or {@code null} if the factory is unable to create a schedule info.
     */
    @Nullable
    private Schedule<InAppMessage> createSchedule(@NonNull Context context, @NonNull LegacyInAppMessage legacyInAppMessage) {
        try {
            Trigger trigger;

            // In terms of the scheduled message model, displayAsap means using an active session trigger.
            // Otherwise the closest analog to the v1 behavior is the foreground trigger.
            if (displayAsapEnabled) {
                trigger = Triggers.newActiveSessionTriggerBuilder().build();
            } else {
                trigger = Triggers.newForegroundTriggerBuilder().build();
            }

            Schedule.Builder<InAppMessage> builder = Schedule.newBuilder(createMessage(context, legacyInAppMessage))
                                                             .addTrigger(trigger)
                                                             .setEnd(legacyInAppMessage.getExpiry())
                                                             .setId(legacyInAppMessage.getId());

            ScheduleBuilderExtender builderExtender = this.scheduleBuilderExtender;
            if (builderExtender != null) {
                builderExtender.extend(context, builder, legacyInAppMessage);
            }

            return builder.build();

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
            NotificationActionButtonGroup group = pushManager.getNotificationActionGroup(legacyMessage.getButtonGroupId());

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
                                                   .setExtras(legacyMessage.getExtras())
                                                   .setSource(InAppMessage.SOURCE_LEGACY_PUSH);

        MessageBuilderExtender builderExtender = this.messageBuilderExtender;
        if (builderExtender != null) {
            builderExtender.extend(context, builder, legacyMessage);
        }
        return builder.build();
    }

}
