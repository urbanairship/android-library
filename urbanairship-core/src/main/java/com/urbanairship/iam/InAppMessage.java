/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.Size;
import android.support.annotation.StringDef;

import com.urbanairship.Logger;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.iam.fullscreen.FullScreenDisplayContent;
import com.urbanairship.iam.html.HtmlDisplayContent;
import com.urbanairship.iam.modal.ModalDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.UAStringUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines an in-app message.
 */
public class InAppMessage implements Parcelable, JsonSerializable {

    /**
     * Max message ID length.
     */
    public static final int MAX_ID_LENGTH = 100;

    // JSON keys
    static final String MESSAGE_ID_KEY = "message_id";
    private static final String DISPLAY_TYPE_KEY = "display_type";
    private static final String DISPLAY_CONTENT_KEY = "display";
    private static final String EXTRA_KEY = "extra";
    private static final String AUDIENCE_KEY = "audience";
    private static final String ACTIONS_KEY = "actions";
    private static final String SOURCE_KEY = "source";
    private static final String CAMPAIGNS_KEY = "campaigns";

    @StringDef({ SOURCE_LEGACY_PUSH, SOURCE_REMOTE_DATA, SOURCE_APP_DEFINED })
    @Retention(RetentionPolicy.SOURCE)
    @interface Source {}

    /**
     * In-app message was generated from a push in the legacy in-app message manager.
     */
    static final String SOURCE_LEGACY_PUSH = "legacy-push";

    /**
     * In-app message from the remote-data service.
     */
    static final String SOURCE_REMOTE_DATA = "remote-data";

    /**
     * In-app message created programmatically by the application.
     */
    static final String SOURCE_APP_DEFINED = "app-defined";

    @StringDef({ TYPE_BANNER, TYPE_CUSTOM, TYPE_FULLSCREEN, TYPE_MODAL, TYPE_HTML })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DisplayType {}

    /**
     * Banner in-app message.
     */
    public static final String TYPE_BANNER = "banner";

    /**
     * Custom in-app message.
     */
    public static final String TYPE_CUSTOM = "custom";

    /**
     * Fullscreen in-app message.
     */
    public static final String TYPE_FULLSCREEN = "fullscreen";

    /**
     * Modal in-app message.
     */
    public static final String TYPE_MODAL = "modal";

    /**
     * HTML in-app message.
     */
    public static final String TYPE_HTML = "html";


    @DisplayType
    private final String type;
    private final JsonMap extras;
    private final String id;
    private final JsonSerializable content;
    private final Audience audience;
    private final Map<String, JsonValue> actions;
    private JsonValue campaigns;

    @Source
    private final String source;


    /**
     * Default constructor.
     *
     * @param builder An InAppMessage builder instance.
     */
    private InAppMessage(Builder builder) {
        this.type = builder.type;
        this.content = builder.content;
        this.id = builder.id;
        this.extras = builder.extras == null ? JsonMap.EMPTY_MAP : builder.extras;
        this.audience = builder.audience;
        this.actions = builder.actions;
        this.source = builder.source;
        this.campaigns = builder.campaigns;
    }

    /**
     * Gets the in-app message type.
     *
     * @return The in-app message type.
     */
    @DisplayType
    public String getType() {
        return type;
    }

    /**
     * Returns the display content.
     * <p>
     * The return type depends on the in-app message type:
     * {@link #TYPE_BANNER}: a {@link com.urbanairship.iam.banner.BannerDisplayContent},
     * {@link #TYPE_CUSTOM}: a {@link com.urbanairship.iam.custom.CustomDisplayContent},
     * {@link #TYPE_FULLSCREEN}: a {@link com.urbanairship.iam.fullscreen.FullScreenDisplayContent}
     *
     * @return The display content.
     */
    public <T extends DisplayContent> T getDisplayContent() {
        if (content == null) {
            return null;
        }
        try {
            //noinspection unchecked
            return (T) content;
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * Gets the message ID.
     *
     * @return The message ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the extras.
     *
     * @return The extras.
     */
    @NonNull
    public JsonMap getExtras() {
        return extras;
    }

    /**
     * Gets the audience.
     *
     * @return The audience.
     */
    @Nullable
    public Audience getAudience() {
        return audience;
    }

    /**
     * Gets the actions.
     *
     * @return The actions.
     */
    public Map<String, JsonValue> getActions() {
        return actions;
    }

    /**
     * The in-app message source.
     *
     * @return The in-app message source.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Source
    String getSource() {
        return source;
    }

    /**
     * The in-app message campaigns info.
     *
     * @return The in-app message campaigns info.
     * @hide
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    JsonValue getCampaigns() {
        return campaigns;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(MESSAGE_ID_KEY, id)
                      .putOpt(EXTRA_KEY, extras)
                      .putOpt(DISPLAY_CONTENT_KEY, content)
                      .putOpt(DISPLAY_TYPE_KEY, type)
                      .putOpt(AUDIENCE_KEY, audience)
                      .putOpt(ACTIONS_KEY, actions)
                      .putOpt(SOURCE_KEY, source)
                      .putOpt(CAMPAIGNS_KEY, campaigns)
                      .build().toJsonValue();
    }

    /**
     * Parses a json value.
     *
     * @param jsonValue The json value.
     * @param defaultSource The default source if its not set in the JSON.
     * @return The parsed InAppMessage.
     * @throws JsonException If the json is invalid.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    static InAppMessage fromJson(JsonValue jsonValue, @Source String defaultSource) throws JsonException {
        String type = jsonValue.optMap().opt(DISPLAY_TYPE_KEY).getString("");
        JsonValue content = jsonValue.optMap().opt(DISPLAY_CONTENT_KEY);


        String messageId = jsonValue.optMap().opt(MESSAGE_ID_KEY).getString();
        if (messageId == null || messageId.length() > MAX_ID_LENGTH) {
            throw new JsonException("Invalid message ID. Must be nonnull and less than or equal to " + MAX_ID_LENGTH + " characters.");
        }


        InAppMessage.Builder builder = InAppMessage.newBuilder()
                                                   .setId(messageId)
                                                   .setExtras(jsonValue.optMap().opt(EXTRA_KEY).optMap())
                                                   .setDisplayContent(type, content);


        // Source
        @Source String source = jsonValue.optMap().opt(SOURCE_KEY).getString(defaultSource);
        if (source != null) {
            builder.setSource(source);
        }

        // Actions
        if (jsonValue.optMap().containsKey(ACTIONS_KEY)) {
            JsonMap jsonMap = jsonValue.optMap().get(ACTIONS_KEY).getMap();
            if (jsonMap == null) {
                throw new JsonException("Actions must be a JSON object: " + jsonValue.optMap().opt(ACTIONS_KEY));
            }

            builder.setActions(jsonMap.getMap());
        }

        // Audience
        if (jsonValue.optMap().containsKey(AUDIENCE_KEY)) {
            builder.setAudience(Audience.parseJson(jsonValue.optMap().opt(AUDIENCE_KEY)));
        }

        // Campaigns
        if (jsonValue.optMap().containsKey(CAMPAIGNS_KEY)) {
            builder.setCampaigns(jsonValue.optMap().opt(CAMPAIGNS_KEY));
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid InAppMessage json.", e);
        }
    }


    /**
     * Parses a json value.
     *
     * @param jsonValue The json value.
     * @return The parsed InAppMessage.
     * @throws JsonException If the json is invalid.
     */
    static InAppMessage fromJson(JsonValue jsonValue) throws JsonException {
        return fromJson(jsonValue, null);
    }


    /**
     * Creates a new builder.
     *
     * @return A new in-app message builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creator for parcelable interface.
     *
     * @hide
     */
    public static final Creator<InAppMessage> CREATOR = new Creator<InAppMessage>() {
        @Override
        public InAppMessage createFromParcel(Parcel in) {
            String payload = in.readString();

            try {
                return fromJson(JsonValue.parseString(payload));
            } catch (JsonException e) {
                Logger.error("InAppMessage - Invalid parcel: " + e);
                return null;
            }
        }

        @Override
        public InAppMessage[] newArray(int size) {
            return new InAppMessage[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(toJsonValue().toString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return toJsonValue().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InAppMessage message = (InAppMessage) o;

        if (type != null ? !type.equals(message.type) : message.type != null) {
            return false;
        }
        if (extras != null ? !extras.equals(message.extras) : message.extras != null) {
            return false;
        }
        if (id != null ? !id.equals(message.id) : message.id != null) {
            return false;
        }
        if (content != null ? !content.equals(message.content) : message.content != null) {
            return false;
        }
        if (audience != null ? !audience.equals(message.audience) : message.audience != null) {
            return false;
        }
        if (actions != null ? !actions.equals(message.actions) : message.actions != null) {
            return false;
        }
        if (campaigns != null ? !campaigns.equals(message.campaigns) : message.campaigns != null) {
            return false;
        }
        return source != null ? source.equals(message.source) : message.source == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (extras != null ? extras.hashCode() : 0);
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (content != null ? content.hashCode() : 0);
        result = 31 * result + (audience != null ? audience.hashCode() : 0);
        result = 31 * result + (actions != null ? actions.hashCode() : 0);
        result = 31 * result + (campaigns != null ? campaigns.hashCode() : 0);
        result = 31 * result + (source != null ? source.hashCode() : 0);
        return result;
    }

    /**
     * In-app message builder.
     */
    public static class Builder {
        @DisplayType
        private String type;
        private JsonMap extras;
        private String id;
        private JsonSerializable content;
        private Audience audience;
        private Map<String, JsonValue> actions = new HashMap<>();

        @Source
        private String source = SOURCE_APP_DEFINED;
        private JsonValue campaigns;

        private Builder() {}

        /**
         * Sets the display content to the parsed type.
         *
         * @param type The type.
         * @param content The display content as a json value.
         * @return The builder object.
         */
        private Builder setDisplayContent(String type, JsonValue content) throws JsonException {
            switch (type) {
                case TYPE_BANNER:
                    this.setDisplayContent(BannerDisplayContent.parseJson(content));
                    break;

                case TYPE_CUSTOM:
                    this.setDisplayContent(CustomDisplayContent.parseJson(content));
                    break;

                case TYPE_FULLSCREEN:
                    this.setDisplayContent(FullScreenDisplayContent.parseJson(content));
                    break;

                case TYPE_MODAL:
                    this.setDisplayContent(ModalDisplayContent.parseJson(content));
                    break;

                case TYPE_HTML:
                    this.setDisplayContent(HtmlDisplayContent.parseJson(content));
                    break;
            }

            return this;
        }

        /**
         * Sets the modal display content and type.
         *
         * @param displayContent The modal display content.
         * @return The builder.
         */
        public Builder setDisplayContent(ModalDisplayContent displayContent) {
            this.type = TYPE_MODAL;
            this.content = displayContent;
            return this;
        }

        /**
         * Sets the full screen display content and type.
         *
         * @param displayContent The full screen display content.
         * @return The builder.
         */
        public Builder setDisplayContent(FullScreenDisplayContent displayContent) {
            this.type = TYPE_FULLSCREEN;
            this.content = displayContent;
            return this;
        }

        /**
         * Sets the banner display content and type.
         *
         * @param displayContent The banner display content.
         * @return The builder.
         */
        public Builder setDisplayContent(BannerDisplayContent displayContent) {
            this.type = TYPE_BANNER;
            this.content = displayContent;
            return this;
        }

        /**
         * Sets the html display content and type.
         *
         * @param displayContent The html display content.
         * @return The builder.
         */
        public Builder setDisplayContent(HtmlDisplayContent displayContent) {
            this.type = TYPE_HTML;
            this.content = displayContent;
            return this;
        }

        /**
         * Sets the source of the in-app message.
         *
         * @param source The in-app message source.
         * @return The builder.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        Builder setSource(@NonNull @Source String source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the campaigns info for the in-app message.
         *
         * @param campaigns The in-app message campaigns info.
         * @return The builder.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        Builder setCampaigns(JsonValue campaigns) {
            this.campaigns = campaigns;
            return this;
        }

        /**
         * Sets the custom display content and type.
         *
         * @param displayContent The custom display content.
         * @return The builder.
         */
        public Builder setDisplayContent(CustomDisplayContent displayContent) {
            this.type = TYPE_CUSTOM;
            this.content = displayContent;
            return this;
        }

        /**
         * Sets the extras.
         *
         * @param extras An extra json map.
         * @return The builder.
         */
        public Builder setExtras(JsonMap extras) {
            this.extras = extras;
            return this;
        }

        /**
         * Sets the in-app message ID.
         *
         * @param id The message ID.
         * @return The builder.
         */
        public Builder setId(@NonNull @Size(min = 1, max = MAX_ID_LENGTH) String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the audience.
         *
         * @param audience The audience.
         * @return The builder.
         */
        public Builder setAudience(Audience audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Sets the actions to run when the in-app message is displayed.
         *
         * @param actions The action map.
         * @return The builder.
         */
        public Builder setActions(Map<String, JsonValue> actions) {
            this.actions.clear();

            if (actions != null) {
                this.actions.putAll(actions);
            }

            return this;
        }

        /**
         * Adds an action to run when the in-app message is displayed.
         *
         * @param actionName The action name.
         * @param actionValue The action value.
         * @return The builder.
         */
        public Builder addAction(@NonNull String actionName, @NonNull JsonValue actionValue) {
            this.actions.put(actionName, actionValue);
            return this;
        }

        /**
         * Builds the in-app message.
         *
         * @return The built in-app message.
         * @throws IllegalArgumentException If the ID is missing, ID length is greater than the {@link #MAX_ID_LENGTH},
         * or if the content is missing.
         */
        public InAppMessage build() {
            Checks.checkArgument(!UAStringUtil.isEmpty(id), "Missing ID.");
            Checks.checkArgument(id.length() <= MAX_ID_LENGTH, "Id exceeds max ID length: " + MAX_ID_LENGTH);
            Checks.checkNotNull(type, "Missing type.");
            Checks.checkNotNull(content, "Missing content.");
            return new InAppMessage(this);
        }
    }
}
