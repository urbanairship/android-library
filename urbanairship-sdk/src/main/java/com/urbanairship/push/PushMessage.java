package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.OpenRichPushInboxAction;
import com.urbanairship.actions.OverlayRichPushMessageAction;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.iam.InAppMessage;
import com.urbanairship.util.UAMathUtil;
import com.urbanairship.util.UAStringUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A push message, usually created from handling a message intent from either GCM,
 * or another push notification service
 */
public class PushMessage implements Parcelable, JsonSerializable {

    /**
     * The rich push extra that contains the rich push message ID.
     */
    public static final String EXTRA_RICH_PUSH_ID = "_uamid";

    /**
     * The ping extra indicates a push meant to test whether the application is active
     */
    static final String EXTRA_PING = "com.urbanairship.push.PING";

    /**
     * The alert extra holds the string sent in the "alert" field of an Urban Airship
     * Push Notification.
     */
    public static final String EXTRA_ALERT = "com.urbanairship.push.ALERT";

    /**
     * The push ID extra holds the unique push ID sent in an Urban Airship
     * Push Notification. This is most commonly referred to as the "Send ID"
     * at Urban Airship.
     */
    public static final String EXTRA_SEND_ID = "com.urbanairship.push.PUSH_ID";

    /**
     * The push metadata extra holds the encrypted push identifiers sent in an
     * Urban Airship Push Notification. Possible data includes send, push, and group IDs.
     */
    public static final String EXTRA_METADATA = "com.urbanairship.metadata";

    /**
     * The actions extra key holds the payload of actions to be performed with the
     * push.
     */
    public static final String EXTRA_ACTIONS = "com.urbanairship.actions";

    /**
     * The extra key for the payload of Urban Airship actions to be run when an
     * interactive notification action button is opened.
     */
    public static final String EXTRA_INTERACTIVE_ACTIONS = "com.urbanairship.interactive_actions";

    /**
     * The extra key for the interactive notification group that will be displayed with a push.
     */
    public static final String EXTRA_INTERACTIVE_TYPE = "com.urbanairship.interactive_type";

    /**
     * The extra key for the title of the notification.
     */
    public static final String EXTRA_TITLE = "com.urbanairship.title";

    /**
     * The extra key for the summary of the notification.
     */
    public static final String EXTRA_SUMMARY = "com.urbanairship.summary";

    /**
     * The extra key for the wearable payload.
     */
    public static final String EXTRA_WEARABLE = "com.urbanairship.wearable";

    /**
     * The extra key for the style of the notification.
     */
    public static final String EXTRA_STYLE = "com.urbanairship.style";

    /**
     * The extra key indicates if the notification should only be displayed on the device.
     */
    public static final String EXTRA_LOCAL_ONLY = "com.urbanairship.local_only";

    /**
     * The extra key indicates the icon color.
     */
    public static final String EXTRA_ICON_COLOR = "com.urbanairship.icon_color";

    /**
     * The extra key indicates the name of an icon to use from an app's drawable resources.
     */
    public static final String EXTRA_ICON = "com.urbanairship.icon";

    /**
     * The extra key for the priority of the notification. Acceptable values range from PRIORITY_MIN
     * (-2) to PRIORITY_MAX (2).
     * <p/>
     * Defaults to 0.
     */
    public static final String EXTRA_PRIORITY = "com.urbanairship.priority";

    /**
     * The extra key for the sound of the notification.
     */
    public static final String EXTRA_SOUND = "com.urbanairship.sound";

    /**
     * The minimum priority value for the notification.
     */
    static final int MIN_PRIORITY = -2;

    /**
     * The maximum priority value for the notification.
     */
    static final int MAX_PRIORITY = 2;

    /**
     * The extra key for the notification's visibility in the lockscreen. Acceptable values are:
     * VISIBILITY_PUBLIC (1), VISIBILITY_PRIVATE (0) or VISIBILITY_SECRET (-1).
     */
    public static final String EXTRA_VISIBILITY = "com.urbanairship.visibility";

    /**
     * The minimum visibility value for the notification in the lockscreen.
     */
    static final int MIN_VISIBILITY = -1;

    /**
     * The maximum visibility value for the notification in the lockscreen.
     */
    static final int MAX_VISIBILITY = 1;

    /**
     * Shows the notification's full content in the lockscreen. This is the default visibility.
     */
    static final int VISIBILITY_PUBLIC = 1;

    /**
     * The extra key for the public notification payload.
     */
    public static final String EXTRA_PUBLIC_NOTIFICATION = "com.urbanairship.public_notification";

    /**
     * The extra key for the category of the notification.
     */
    public static final String EXTRA_CATEGORY = "com.urbanairship.category";

    /**
     * The push ID extra is the ID assigned to a push at the time it is sent.
     * Each API call will result in a unique push ID, so all notifications that are part of a
     * multicast push will have the same push ID.
     */
    public static final String EXTRA_PUSH_ID = "com.urbanairship.push.CANONICAL_PUSH_ID";

    /**
     * The EXPIRATION extra is a time expressed in seconds since the Epoch after which, if specified, the
     * notification should not be delivered. It is removed from the notification before delivery to the
     * client. If not present, notifications may be delivered arbitrarily late.
     */
    public static final String EXTRA_EXPIRATION = "com.urbanairship.push.EXPIRATION";

    /**
     * The extra key for the {@link com.urbanairship.push.iam.InAppMessage} payload.
     */
    public static final String EXTRA_IN_APP_MESSAGE = "com.urbanairship.in_app";

    /**
     * The extra key for the tag to be used when posting a notification.
     */
    public static final String EXTRA_NOTIFICATION_TAG = "com.urbanairship.notification_tag";

    /**
     * The extra key for the channel to be used when posting a notification.
     */
    public static final String EXTRA_NOTIFICATION_CHANNEL = "com.urbanairship.notification_channel";

    /**
     * The extra key for the delivery priority.
     */
    public static final String EXTRA_DELIVERY_PRIORITY = "com.urbanairship.priority";

    /**
     * Constant for the extra {@link #EXTRA_DELIVERY_PRIORITY} that indicates the push is high priority.
     */
    public static final String PRIORITY_HIGH = "high";

    /**
     * Default sound name.
     */
    private static final String DEFAULT_SOUND_NAME = "default";

    private static final List<String> INBOX_ACTION_NAMES = Arrays.asList(
            OpenRichPushInboxAction.DEFAULT_REGISTRY_NAME,
            OpenRichPushInboxAction.DEFAULT_REGISTRY_SHORT_NAME,
            OverlayRichPushMessageAction.DEFAULT_REGISTRY_NAME,
            OverlayRichPushMessageAction.DEFAULT_REGISTRY_SHORT_NAME);

    private Bundle pushBundle;
    private final Map<String, String> data;

    private Uri sound = null;

    /**
     * Create a new PushMessage
     *
     * @param pushBundle The intent extras for the push
     */
    public PushMessage(@NonNull Bundle pushBundle) {
        this.pushBundle = pushBundle;

        this.data = new HashMap<>();
        for (String key : pushBundle.keySet()) {
            Object value = pushBundle.get(key);

            if (value != null) {
                data.put(key, String.valueOf(value));
            }
        }
    }

    /**
     * Create a new PushMessage
     *
     * @param data The push data.
     */
    public PushMessage(Map<String, String> data) {
        this.data = new HashMap<>(data);
    }

    /**
     * Checks if the expiration exists and is expired
     *
     * @return <code>true</code> if the message is expired, otherwise <code>false</code>
     */
    boolean isExpired() {
        String expirationStr = data.get(EXTRA_EXPIRATION);
        if (!UAStringUtil.isEmpty(expirationStr)) {
            Logger.debug("Notification expiration time is \"" + expirationStr + "\"");
            try {
                long expiration = Long.parseLong(expirationStr) * 1000;
                if (expiration < System.currentTimeMillis()) {
                    return true;
                }
            } catch (NumberFormatException e) {
                Logger.debug("Ignoring malformed expiration time: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Checks if the message is ping or not
     *
     * @return <code>true</code> if the message is a ping to test if
     * application is active, otherwise <code>false</code>
     */
    boolean isPing() {
        return data.containsKey(EXTRA_PING);
    }

    /**
     * Gets an extra from the push bundle.
     *
     * @param key The extra key.
     * @param defaultValue Default value if the value does not exist.
     * @return The extra or the default value if the extra does not exist.
     */
    public String getExtra(String key, String defaultValue) {
        if (data.get(key) != null) {
            return data.get(key);
        }

        return defaultValue;
    }

    /**
     * Checks the message for Urban Airship keys.
     *
     * @return <code>true</code> if the message contains any Urban Airship keys.
     */
    public boolean containsAirshipKeys() {
        for (String key : data.keySet()) {
            if (key.startsWith("com.urbanairship")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the message's canonical push ID
     *
     * @return The canonical push ID
     */
    @Nullable
    public String getCanonicalPushId() {
        return data.get(EXTRA_PUSH_ID);
    }

    /**
     * Gets the rich push message ID
     *
     * @return The rich push message ID, or null if its unavailable.
     */
    @Nullable
    public String getRichPushMessageId() {
        return data.get(EXTRA_RICH_PUSH_ID);
    }

    /**
     * Gets the notification alert
     *
     * @return The notification alert.
     */
    @Nullable
    public String getAlert() {
        return data.get(EXTRA_ALERT);
    }

    /**
     * Gets the push send ID
     *
     * @return The push send Id.
     */
    @Nullable
    public String getSendId() {
        return  data.get(EXTRA_SEND_ID);
    }

    /**
     * Gets the push send metadata.
     *
     * @return The push send metadata.
     */
    @Nullable
    public String getMetadata() {
        return  data.get(EXTRA_METADATA);
    }

    /**
     * Returns a bundle of all the push extras
     *
     * @return A bundle of all the push extras
     */
    @NonNull
    public Bundle getPushBundle() {
        if (pushBundle == null) {
            pushBundle = new Bundle();
            for (Map.Entry<String, String> entry : data.entrySet()) {
                pushBundle.putString(entry.getKey(), entry.getValue());
            }
        }

        return pushBundle;
    }

    /**
     * Gets the push message's actions.
     *
     * @return A map of action name to action value.
     */
    @NonNull
    public Map<String, ActionValue> getActions() {
        String actionsPayload = data.get(EXTRA_ACTIONS);
        Map<String, ActionValue> actions = new HashMap<>();

        try {
            JsonMap actionsJson = JsonValue.parseString(actionsPayload).getMap();
            if (actionsJson != null) {
                for (Map.Entry<String, JsonValue> entry : actionsJson) {
                    actions.put(entry.getKey(), new ActionValue(entry.getValue()));
                }
            }
        } catch (JsonException e) {
            Logger.error("Unable to parse action payload: " + actionsPayload);
            return actions;
        }

        if (!UAStringUtil.isEmpty(getRichPushMessageId())) {
            if (Collections.disjoint(actions.keySet(), INBOX_ACTION_NAMES)) {
                actions.put(OpenRichPushInboxAction.DEFAULT_REGISTRY_SHORT_NAME, ActionValue.wrap(getRichPushMessageId()));
            }
        }

        return actions;
    }

    /**
     * Gets the notification actions payload.
     *
     * @return The notification actions payload.
     */
    @Nullable
    public String getInteractiveActionsPayload() {
        return data.get(EXTRA_INTERACTIVE_ACTIONS);
    }

    /**
     * Gets the notification action button type.
     *
     * @return The interactive notification type.
     */
    @Nullable
    public String getInteractiveNotificationType() {
        return data.get(EXTRA_INTERACTIVE_TYPE);
    }

    /**
     * Gets the title of the notification.
     *
     * @return The title of the notification.
     */
    @Nullable
    public String getTitle() {
        return data.get(EXTRA_TITLE);
    }

    /**
     * Gets the summary of the notification.
     *
     * @return The summary of the notification.
     */
    @Nullable
    public String getSummary() {
        return data.get(EXTRA_SUMMARY);
    }

    /**
     * Gets the wearable payload.
     *
     * @return The wearable payload.
     */
    @Nullable
    public String getWearablePayload() {
        return data.get(EXTRA_WEARABLE);
    }

    /**
     * Gets the style payload of the notification.
     *
     * @return The style payload of the notification.
     */
    @Nullable
    public String getStylePayload() {
        return data.get(EXTRA_STYLE);
    }

    /**
     * Checks if the notification should only be displayed on the device.
     *
     * @return <code>true</code> if the notification should only be displayed on the device,
     * otherwise <code>false</code>
     * <p/>
     * Defaults to false.
     */
    public boolean isLocalOnly() {
        String value = data.get(EXTRA_LOCAL_ONLY);
        return Boolean.parseBoolean(value);
    }

    /**
     * Gets the priority of the notification.
     * <p/>
     * Defaults to 0.
     *
     * @return The priority of the notification.
     */
    public int getPriority() {
        try {
            String value = data.get(EXTRA_PRIORITY);
            return UAMathUtil.constrain(Integer.parseInt(value), MIN_PRIORITY, MAX_PRIORITY);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Gets the visibility of the notification for the lockscreen.
     * <p/>
     * Defaults to 1 (VISIBILITY_PUBLIC).
     *
     * @return The visibility of the notification for the lockscreen.
     */
    public int getVisibility() {
        try {
            String value = data.get(EXTRA_VISIBILITY);
            return UAMathUtil.constrain(Integer.parseInt(value), MIN_VISIBILITY, MAX_VISIBILITY);
        } catch (NumberFormatException e) {
            return VISIBILITY_PUBLIC;
        }
    }

    /**
     * Gets the public notification payload.
     *
     * @return The public notification payload.
     */
    @Nullable
    public String getPublicNotificationPayload() {
        return data.get(EXTRA_PUBLIC_NOTIFICATION);
    }

    /**
     * Gets the category of the notification.
     *
     * @return The category of the notification.
     */
    @Nullable
    public String getCategory() {
        return data.get(EXTRA_CATEGORY);
    }

    /**
     * Gets the sound of the notification.
     *
     * @param context The application context.
     * @return The sound of the notification.
     */
    @Nullable
    public Uri getSound(@NonNull Context context) {
        if (sound == null && data.get(EXTRA_SOUND) != null) {
            String notificationSoundName = data.get(EXTRA_SOUND);

            int id = context.getResources().getIdentifier(notificationSoundName, "raw", context.getPackageName());
            if (id != 0) {
                sound = Uri.parse("android.resource://" + context.getPackageName() + "/" + id);
            } else if (!DEFAULT_SOUND_NAME.equals(notificationSoundName)) {
                // Do not log a warning for the "default" name. Android plays the default sound if no sound
                // is provided.
                Logger.warn("PushMessage - unable to find notification sound with name: " + notificationSoundName);
            }
        }

        return sound;
    }

    /**
     * Gets the notification icon color.
     *
     * @return The color of the icon.
     */
    public int getIconColor(int defaultColor) {
        String colorString = data.get(EXTRA_ICON_COLOR);
        if (colorString != null) {
            try {
                return Color.parseColor(colorString);
            } catch (IllegalArgumentException e) {
                Logger.warn("Unrecognized icon color string: " + colorString + ". Using default color: " + defaultColor);
            }
        }

        return defaultColor;
    }

    /**
     * Gets the notification icon image.
     *
     * @return The integer resource of the icon image.
     */
    @DrawableRes
    public int getIcon(Context context, int defaultIcon) {
        String resourceString = data.get(EXTRA_ICON);
        if (resourceString != null) {
            int iconId = context.getResources().getIdentifier(resourceString, "drawable", context.getPackageName());
            if (iconId != 0) {
                return iconId;
            } else {
                Logger.warn("PushMessage - unable to find icon drawable with name: " + resourceString + ". Using default icon: " + defaultIcon);
            }
        }

        return defaultIcon;
    }

    /**
     * Gets the {@link com.urbanairship.push.iam.InAppMessage} from the push bundle.
     *
     * @return The in-app message.
     */
    @Nullable
    public InAppMessage getInAppMessage() {
        if (data.containsKey(EXTRA_IN_APP_MESSAGE)) {
            try {
                InAppMessage rawMessage = InAppMessage.parseJson(data.get(EXTRA_IN_APP_MESSAGE));
                if (rawMessage == null) {
                    return null;
                }

                InAppMessage.Builder builder = new InAppMessage.Builder(rawMessage)
                        .setId(getSendId());

                if (!UAStringUtil.isEmpty(getRichPushMessageId())) {
                    if (Collections.disjoint(rawMessage.getClickActionValues().keySet(), INBOX_ACTION_NAMES)) {
                        HashMap<String, ActionValue> actions = new HashMap<>(rawMessage.getClickActionValues());
                        actions.put(OpenRichPushInboxAction.DEFAULT_REGISTRY_SHORT_NAME, new ActionValue(JsonValue.wrap(getRichPushMessageId())));
                        builder.setClickActionValues(actions);
                    }
                }

                return builder.create();

            } catch (JsonException e) {
                Logger.error("PushMessage - unable to create in-app message from push payload", e);
            }
        }

        return null;
    }

    /**
     * Returns the notification tag that should be used when posting the notification.
     *
     * @return Either the notification tag or {@code null} if the tag is not available.
     */
    @Nullable
    public String getNotificationTag() {
        return data.get(EXTRA_NOTIFICATION_TAG);
    }

    /**
     * Returns the notification channel that should be used when posting the notification.
     *
     * @return Either the notification channel or {@code null} if the channel is not available.
     */
    @Nullable
    public String getNotificationChannel() {
        return data.get(EXTRA_NOTIFICATION_CHANNEL);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PushMessage that = (PushMessage) o;

        return this.data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return this.data.hashCode();
    }

    /**
     * Gets the push message from the intent if available.
     *
     * @param intent The intent.
     * @return The intent's PushMessage or null if the intent does not contain a PushMessage.
     * @hide
     */
    @Nullable
    public static PushMessage fromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }

        Bundle pushBundle = intent.getBundleExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE);
        if (pushBundle == null) {
            return null;
        }

        return new PushMessage(pushBundle);
    }

    @Override
    public String toString() {
        return data.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBundle(getPushBundle());
    }

    /**
     * Parcel Creator for push messages.
     */
    public static final Parcelable.Creator<PushMessage> CREATOR = new Parcelable.Creator<PushMessage>() {

        @Override
        public PushMessage createFromParcel(Parcel in) {
            return new PushMessage(in.readBundle(PushMessage.class.getClassLoader()));
        }

        @Override
        public PushMessage[] newArray(int size) {
            return new PushMessage[size];
        }
    };

    @Override
    public JsonValue toJsonValue() {
        return JsonValue.wrapOpt(data);
    }


    public static PushMessage fromJsonValue(JsonValue jsonValue) {
        Map<String, String> data = new HashMap<>();
        for (Map.Entry<String, JsonValue> entry : jsonValue.optMap().entrySet()) {
            if (entry.getValue().isString()) {
                data.put(entry.getKey(), entry.getValue().getString());
            } else {
                data.put(entry.getKey(), entry.getValue().toString());
            }
        }

        return new PushMessage(data);
    }
}
