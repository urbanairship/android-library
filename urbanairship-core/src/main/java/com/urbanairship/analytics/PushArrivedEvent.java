/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.notifications.NotificationChannelCompat;
import com.urbanairship.util.UAStringUtil;

/**
 * Analytics event when a push arrives.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PushArrivedEvent extends Event {

    @NonNull
    static final String TYPE = "push_arrived";

    /**
     * Default send ID assigned when absent from the push payload.
     */
    private static final String DEFAULT_SEND_ID = "MISSING_SEND_ID";

    /**
     * The notification channel key.
     */
    private static final String NOTIFICATION_CHANNEL_KEY = "notification_channel";

    /**
     * The notification channel ID key.
     */
    private static final String NOTIFICATION_CHANNEL_ID_KEY = "identifier";

    /**
     * The notification ID key.
     */
    private static final String NOTIFICATION_CHANNEL_IMPORTANCE_KEY = "importance";

    /**
     * The notification channel group key.
     */
    private static final String NOTIFICATION_CHANNEL_GROUP_KEY = "group";

    /**
     * The notification channel group blocked key.
     */
    private static final String NOTIFICATION_CHANNEL_GROUP_BLOCKED = "blocked";

    private final PushMessage message;
    private NotificationChannelCompat notificationChannel;

    /**
     * Constructor for PushArrivedEvent. You should not instantiate this class directly.
     *
     * @param message The associated PushMessage.
     */
    public PushArrivedEvent(@NonNull PushMessage message) {
        this(message, null);
    }

    /**
     * Constructor for PushArrivedEvent. You should not instantiate this class directly.
     *
     * @param message The associated PushMessage.
     * @param notificationChannel The notification channel.
     */
    public PushArrivedEvent(@NonNull PushMessage message, @Nullable NotificationChannelCompat notificationChannel) {
        this.message = message;
        this.notificationChannel = notificationChannel;
    }

    private String importanceString(int importance) {
        switch (importance) {
            case 0:
                return "NONE";
            case 1:
                return "MIN";
            case 2:
                return "LOW";
            case 3:
                return "DEFAULT";
            case 4:
                return "HIGH";
            case 5:
                return "MAX";
            default:
                return "UNKNOWN";
        }
    }

    private void addNotificationChannelData(JsonMap.Builder builder) {
        String importance = importanceString(notificationChannel.getImportance());
        String groupId = notificationChannel.getGroup();
        JsonMap notificationGroupMap = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (groupId != null) {
                NotificationManager nm = (NotificationManager) UAirship.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationChannelGroup group = nm.getNotificationChannelGroup(groupId);
                String blocked = String.valueOf(group.isBlocked());

                notificationGroupMap = JsonMap.newBuilder()
                                              .put(NOTIFICATION_CHANNEL_GROUP_KEY, JsonMap.newBuilder()
                                                                                          .putOpt(NOTIFICATION_CHANNEL_GROUP_BLOCKED, blocked)
                                                                                          .build())
                                              .build();
            }
        }

        builder.put(NOTIFICATION_CHANNEL_KEY, JsonMap.newBuilder()
                                                     .put(NOTIFICATION_CHANNEL_ID_KEY, notificationChannel.getId())
                                                     .put(NOTIFICATION_CHANNEL_IMPORTANCE_KEY, importance)
                                                     .putOpt(NOTIFICATION_CHANNEL_GROUP_KEY, notificationGroupMap)
                                                     .build());
    }

    @NonNull
    @Override
    public final String getType() {
        return TYPE;
    }

    @NonNull
    @Override
    protected final JsonMap getEventData() {

        JsonMap.Builder builder = JsonMap.newBuilder()
                                         .put(PUSH_ID_KEY, !UAStringUtil.isEmpty(message.getSendId()) ? message.getSendId() : DEFAULT_SEND_ID)
                                         .put(METADATA_KEY, message.getMetadata())
                                         .put(CONNECTION_TYPE_KEY, getConnectionType())
                                         .put(CONNECTION_SUBTYPE_KEY, getConnectionSubType())
                                         .put(CARRIER_KEY, getCarrier());

        if (notificationChannel != null) {
            addNotificationChannelData(builder);
        }

        return builder.build();
    }

}
