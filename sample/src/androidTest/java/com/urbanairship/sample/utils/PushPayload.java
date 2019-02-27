/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.utils;

import android.support.annotation.NonNull;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Collections;

/**
 * Helper class to create the push payload.
 */
public class PushPayload implements JsonSerializable {

    static final String AUDIENCE = "audience";
    static final String DEVICE_TYPES = "device_types";
    static final String NOTIFICATION = "notification";
    static final String MESSAGE = "message";
    static final String IN_APP = "in_app";


    private JsonSerializable audience;
    private String alert;
    private RichPushPayload richPushPayload;
    private InAppMessagePayload inAppMessagePayload;

    /**
     * Default constructor.
     *
     * @param builder The PushPayload builder instance.
     */
    private PushPayload(Builder builder) {
        this.audience = builder.audience;
        this.alert = builder.alert;
        this.richPushPayload = builder.richPushPayload;
        this.inAppMessagePayload = builder.inAppMessagePayload;
    }

    /**
     * Get alert string.
     *
     * @return the alert string.
     */
    public String getAlert() {
        return alert;
    }

    /**
     * Create an alias push builder.
     *
     * @param alias The alias string.
     * @return The builder object.
     */
    public static Builder newAliasPushBuilder(String alias) {
        return new Builder("alias", alias);
    }

    /**
     * Create a channel push builder.
     *
     * @param channel The channel string.
     * @return The builder object.
     */
    public static Builder newChannelPushBuilder(String channel) {
        return new Builder("android_channel", channel);
    }

    /**
     * Create a named user push builder.
     *
     * @param namedUser The named user string.
     * @return The builder object.
     */
    public static Builder newNamedUserPushBuilder(String namedUser) {
        return new Builder("named_user", namedUser);
    }

    /**
     * Create a tag push builder.
     *
     * @param tag The tag string.
     * @return The builder object.
     */
    public static Builder newTagPushBuilder(String tag) {
        return new Builder("tag", tag);
    }

    /**
     * Create a broadcast push builder.
     *
     * @return The builder object.
     */
    public static Builder newBroadcastPushBuilder() {
        return new Builder();
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        JsonMap payload = JsonMap.newBuilder()
                                 .putOpt(AUDIENCE, audience)
                                 .putOpt(NOTIFICATION, JsonMap.newBuilder()
                                                              .putOpt("alert", alert)
                                                              .build())
                                 .putOpt(MESSAGE, richPushPayload)
                                 .putOpt(IN_APP, inAppMessagePayload)
                                 .putOpt(DEVICE_TYPES, JsonValue.wrapOpt(Collections.singletonList("android")))
                                 .build();

        return payload.toJsonValue();
    }

    /**
     * The PushPayload as a JSON formatted string.
     *
     * @return The push payload as a JSON formatted string.
     */
    @Override
    public String toString() {
        return this.toJsonValue().toString();
    }

    /**
     * Builds the PushPayload
     */
    public static class Builder {
        private JsonSerializable audience;
        private String alert;
        private RichPushPayload richPushPayload;
        private InAppMessagePayload inAppMessagePayload;

        /**
         * Set the audience.
         *
         * @param audience The audience string.
         * @param identifier The identifier string.
         */
        private Builder(String audience, String identifier) {
            JsonMap.Builder audiencePayload = JsonMap.newBuilder();
            audiencePayload.put(audience, identifier);
            this.audience = audiencePayload.build();
        }

        /**
         * Set the audience as broadcast.
         */
        private Builder() {
            this.audience = JsonValue.wrap("all");
        }

        /**
         * Set the alert notification.
         *
         * @param alert The alert string.
         * @return The builder object.
         */
        public Builder setAlert(String alert) {
            this.alert = alert;
            return this;
        }

        /**
         * Set the rich push message.
         *
         * @param richPushPayload The rich push payload.
         * @return The builder object.
         */
        public Builder setRichPushMessage(RichPushPayload richPushPayload) {
            this.richPushPayload = richPushPayload;
            return this;
        }

        /**
         * Set the in-app message.
         *
         * @param inApp The in-app message payload.
         * @return The builder object.
         */
        public Builder setInAppMessage(InAppMessagePayload inApp) {
            this.inAppMessagePayload = inApp;
            return this;
        }

        /**
         * Creates the push payload.
         *
         * @return The builder object.
         */
        public PushPayload build() {
            return new PushPayload(this);
        }
    }


}
