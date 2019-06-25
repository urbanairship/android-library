/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.actions.OpenRichPushInboxAction;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.Checks;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.urbanairship.richpush.RichPushInbox.INBOX_ACTION_NAMES;

/**
 * Legacy in-app message model object.
 */
public class LegacyInAppMessage {

    // JSON keys
    private static final String BANNER_TYPE = "banner";
    private static final String DISPLAY_KEY = "display";
    private static final String ACTIONS_KEY = "actions";
    private static final String TYPE_KEY = "type";
    private static final String EXTRA_KEY = "extra";
    private static final String ALERT_KEY = "alert";
    private static final String PRIMARY_COLOR_KEY = "primary_color";
    private static final String SECONDARY_COLOR_KEY = "secondary_color";
    private static final String DURATION_KEY = "duration";
    private static final String EXPIRY_KEY = "expiry";
    private static final String POSITION_KEY = "position";
    private static final String ON_CLICK_KEY = "on_click";
    private static final String BUTTON_GROUP_KEY = "button_group";
    private static final String BUTTON_ACTIONS_KEY = "button_actions";

    private static final long DEFAULT_EXPIRY_MS = 2592000000L; // 30 days

    private final long expiryMS;
    private final String alert;
    private final Long durationMilliseconds;
    private final Integer primaryColor;
    private final Integer secondaryColor;
    private final String buttonGroupId;
    private final String id;

    @BannerDisplayContent.Placement
    private final String placement;
    private final Map<String, JsonValue> clickActionValues;
    private final JsonMap extras;
    private final Map<String, Map<String, JsonValue>> buttonActionValues;

    private LegacyInAppMessage(@NonNull Builder builder) {
        this.expiryMS = builder.expiryMS == null ? System.currentTimeMillis() + DEFAULT_EXPIRY_MS : builder.expiryMS;
        this.extras = builder.extras == null ? JsonMap.EMPTY_MAP : builder.extras;
        this.alert = builder.alert;
        this.durationMilliseconds = builder.durationMilliseconds;
        this.buttonGroupId = builder.buttonGroupId;
        this.buttonActionValues = builder.buttonActionValues;
        this.clickActionValues = builder.clickActionValues;
        this.placement = builder.placement;
        this.primaryColor = builder.primaryColor;
        this.secondaryColor = builder.secondaryColor;
        this.id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
    }

    /**
     * Returns the expiration time in milliseconds since Jan. 1, 1970, midnight GMT.
     *
     * @return The expiration time in milliseconds since Jan. 1, 1970, midnight GMT.
     */
    public long getExpiry() {
        return expiryMS;
    }

    /**
     * Returns extras map.
     *
     * @return The extras map.
     */
    @NonNull
    public JsonMap getExtras() {
        return extras;
    }

    /**
     * Returns the message's alert.
     *
     * @return The message's alert.
     */
    @Nullable
    public String getAlert() {
        return alert;
    }

    /**
     * Returns the on click action name to action value map.
     *
     * @return The on click action values.
     */
    @NonNull
    public Map<String, JsonValue> getClickActionValues() {
        return Collections.unmodifiableMap(clickActionValues);
    }

    /**
     * Returns the specified button's action name to action value map.
     *
     * @return The button's action values.
     */
    @Nullable
    public Map<String, JsonValue> getButtonActionValues(@NonNull String buttonId) {
        if (buttonActionValues.containsKey(buttonId)) {
            return Collections.unmodifiableMap(buttonActionValues.get(buttonId));
        } else {
            return null;
        }
    }

    /**
     * Returns the button group ID. The button group can be fetched from {@link com.urbanairship.push.PushManager#getNotificationActionGroup(String)}
     *
     * @return The button group ID.
     */
    @Nullable
    public String getButtonGroupId() {
        return buttonGroupId;
    }

    /**
     * Returns the duration in milliseconds for how long the message should be shown.
     *
     * @return The duration of the message in milliseconds.
     */
    @Nullable
    public Long getDuration() {
        return durationMilliseconds;
    }

    /**
     * Returns the placement of the in-app message.
     *
     * @return The message's placement.
     */
    @NonNull
    @BannerDisplayContent.Placement
    public String getPlacement() {
        return placement;
    }

    /**
     * Returns the message's primary color.
     *
     * @return The message's primary color.
     */
    @Nullable
    public Integer getPrimaryColor() {
        return primaryColor;
    }

    /**
     * Returns the message's secondary color.
     *
     * @return The message's secondary color.
     */
    @Nullable
    public Integer getSecondaryColor() {
        return secondaryColor;
    }

    /**
     * Returns the message's ID.
     *
     * @return The message's ID.
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Creates an in-app message from a push message.
     *
     * @param pushMessage The push message.
     * @return The in-app message or null if the push did not contain an in-app message.
     * @throws JsonException If the JSON payload is unable to parsed.
     */
    @Nullable
    public static LegacyInAppMessage fromPush(@NonNull PushMessage pushMessage) throws JsonException {
        if (!pushMessage.containsKey(PushMessage.EXTRA_IN_APP_MESSAGE)) {
            return null;
        }

        JsonValue jsonValue = JsonValue.parseString(pushMessage.getExtra(PushMessage.EXTRA_IN_APP_MESSAGE, ""));
        JsonMap displayJson = jsonValue.optMap().opt(DISPLAY_KEY).optMap();
        JsonMap actionsJson = jsonValue.optMap().opt(ACTIONS_KEY).optMap();

        if (!BANNER_TYPE.equals(displayJson.opt(TYPE_KEY).getString())) {
            throw new JsonException("Only banner types are supported.");
        }

        Builder builder = newBuilder();

        builder.setExtras(jsonValue.optMap().opt(EXTRA_KEY).optMap())
               .setAlert(displayJson.opt(ALERT_KEY).getString());

        // Primary color
        if (displayJson.containsKey(PRIMARY_COLOR_KEY)) {
            try {
                builder.setPrimaryColor(Color.parseColor(displayJson.opt(PRIMARY_COLOR_KEY).optString()));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid primary color: " + displayJson.opt(PRIMARY_COLOR_KEY), e);
            }
        }

        // Secondary color
        if (displayJson.containsKey(SECONDARY_COLOR_KEY)) {
            try {
                builder.setSecondaryColor(Color.parseColor(displayJson.opt(SECONDARY_COLOR_KEY).optString()));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid secondary color: " + displayJson.opt(SECONDARY_COLOR_KEY), e);
            }
        }

        // Duration
        if (displayJson.containsKey(DURATION_KEY)) {
            long duration = displayJson.opt(DURATION_KEY).getLong(0);
            builder.setDuration(TimeUnit.SECONDS.toMillis(duration));
        }

        // Expiry
        long defaultExpiry = System.currentTimeMillis() + DEFAULT_EXPIRY_MS;
        if (jsonValue.optMap().containsKey(EXPIRY_KEY)) {
            builder.setExpiry(DateUtils.parseIso8601(jsonValue.optMap().opt(EXPIRY_KEY).optString(), defaultExpiry));
        } else {
            builder.setExpiry(defaultExpiry);
        }

        // Placement
        if (BannerDisplayContent.PLACEMENT_TOP.equalsIgnoreCase(displayJson.opt(POSITION_KEY).getString())) {
            builder.setPlacement(BannerDisplayContent.PLACEMENT_TOP);
        } else {
            builder.setPlacement(BannerDisplayContent.PLACEMENT_BOTTOM);
        }

        // On click actions
        Map<String, JsonValue> clickActions = actionsJson.opt(ON_CLICK_KEY).optMap().getMap();
        if (!UAStringUtil.isEmpty(pushMessage.getRichPushMessageId())) {
            if (Collections.disjoint(clickActions.keySet(), INBOX_ACTION_NAMES)) {
                clickActions.put(OpenRichPushInboxAction.DEFAULT_REGISTRY_SHORT_NAME, JsonValue.wrapOpt(pushMessage.getRichPushMessageId()));
            }

        }
        builder.setClickActionValues(clickActions);

        // Button group
        builder.setButtonGroupId(actionsJson.opt(BUTTON_GROUP_KEY).getString());

        // Button actions
        JsonMap buttonActionsJson = actionsJson.opt(BUTTON_ACTIONS_KEY).optMap();
        for (Map.Entry<String, JsonValue> entry : buttonActionsJson.entrySet()) {
            String buttonId = entry.getKey();
            JsonMap actionJson = buttonActionsJson.opt(buttonId).optMap();
            builder.setButtonActionValues(buttonId, actionJson.getMap());
        }

        // ID
        builder.setId(pushMessage.getSendId());

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid legacy in-app message" + jsonValue, e);
        }
    }

    /**
     * Creates a new builder.
     *
     * @return A new legacy in-app message builder.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * InAppMessage Builder.
     */
    public static class Builder {

        @NonNull
        private final Map<String, JsonValue> clickActionValues = new HashMap<>();
        private String id;

        @Nullable
        private JsonMap extras;

        @NonNull
        private final Map<String, Map<String, JsonValue>> buttonActionValues = new HashMap<>();

        private String buttonGroupId;
        private String alert;
        private Long expiryMS;
        private Long durationMilliseconds;

        private Integer primaryColor;
        private Integer secondaryColor;
        @NonNull
        @BannerDisplayContent.Placement
        private String placement = BannerDisplayContent.PLACEMENT_BOTTOM;

        /**
         * Default constructor.
         */
        private Builder() {
        }

        /**
         * Sets the message's expiry in milliseconds. If no expiry is set, it will default
         * to 30 days from the creation date.
         *
         * @param milliseconds The expiry date in milliseconds.
         * @return The builder.
         */
        @NonNull
        public Builder setExpiry(@Nullable Long milliseconds) {
            this.expiryMS = milliseconds;
            return this;
        }

        @NonNull
        public Builder setId(@Nullable String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the message's extras.
         *
         * @param extras The message's extras.
         * @return The builder.
         */
        @NonNull
        public Builder setExtras(@Nullable JsonMap extras) {
            this.extras = extras;
            return this;
        }

        /**
         * Sets the message's on click action values.
         *
         * @param actionValues The message's on click action values.
         * @return The builder.
         */
        @NonNull
        public Builder setClickActionValues(@Nullable Map<String, JsonValue> actionValues) {
            this.clickActionValues.clear();
            if (actionValues != null) {
                this.clickActionValues.putAll(actionValues);
            }
            return this;
        }

        /**
         * Sets the message's button actions for a given button ID.
         *
         * @param buttonId The button's ID.
         * @param actionValues The button's action values.
         * @return The builder.
         */
        @NonNull
        public Builder setButtonActionValues(@NonNull String buttonId, @Nullable Map<String, JsonValue> actionValues) {
            if (actionValues == null) {
                buttonActionValues.remove(buttonId);
            } else {
                buttonActionValues.put(buttonId, new HashMap<>(actionValues));
            }
            return this;
        }

        /**
         * Sets the message's button group ID.
         *
         * @param buttonGroupId The message's button group ID.
         * @return The builder.
         */
        @NonNull
        public Builder setButtonGroupId(@Nullable String buttonGroupId) {
            this.buttonGroupId = buttonGroupId;
            return this;
        }

        /**
         * Sets the message's alert.
         *
         * @param alert The message's alert.
         * @return The builder.
         */
        @NonNull
        public Builder setAlert(@Nullable String alert) {
            this.alert = alert;
            return this;
        }

        /**
         * Sets the duration to show the message for.
         *
         * @param milliseconds The duration in milliseconds.
         * @return The builder.
         */
        @NonNull
        public Builder setDuration(@Nullable Long milliseconds) {
            this.durationMilliseconds = milliseconds;
            return this;
        }

        /**
         * Sets the message's placement.
         *
         * @param placement The message's placement.
         * @return The builder.
         */
        @NonNull
        public Builder setPlacement(@BannerDisplayContent.Placement @NonNull String placement) {
            this.placement = placement;
            return this;
        }

        /**
         * Sets the message's primary color.
         *
         * @param color The message's primary color.
         * @return The builder.
         */
        @NonNull
        public Builder setPrimaryColor(@Nullable Integer color) {
            this.primaryColor = color;
            return this;
        }

        /**
         * Sets the message's secondary color.
         *
         * @param color The message's secondary color.
         * @return The builder.
         */
        @NonNull
        public Builder setSecondaryColor(@Nullable Integer color) {
            this.secondaryColor = color;
            return this;
        }

        /**
         * Builds the message.
         *
         * @return The created in-app message.
         * @throws IllegalArgumentException if the duration is less than or equal to 0.
         */
        @NonNull
        public LegacyInAppMessage build() {
            Checks.checkArgument(durationMilliseconds == null || durationMilliseconds > 0, "Duration must be greater than 0");
            return new LegacyInAppMessage(this);
        }

    }

}
