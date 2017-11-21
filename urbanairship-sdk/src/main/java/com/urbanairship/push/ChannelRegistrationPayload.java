/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Model object encapsulating the data relevant to a creation or updates processed by ChannelApiClient.
 */
class ChannelRegistrationPayload implements JsonSerializable {
    static final String CHANNEL_KEY = "channel";
    static final String DEVICE_TYPE_KEY = "device_type";
    static final String OPT_IN_KEY = "opt_in";
    static final String BACKGROUND_ENABLED_KEY = "background";
    static final String ALIAS_KEY = "alias";
    static final String PUSH_ADDRESS_KEY = "push_address";
    static final String SET_TAGS_KEY = "set_tags";
    static final String TAGS_KEY = "tags";
    static final String IDENTITY_HINTS_KEY = "identity_hints";
    static final String USER_ID_KEY = "user_id";
    static final String APID_KEY = "apid";
    static final String TIMEZONE_KEY = "timezone";
    static final String LANGUAGE_KEY = "locale_language";
    static final String COUNTRY_KEY = "locale_country";

    private final boolean optIn;
    private final boolean backgroundEnabled;
    private final String alias;
    private final String deviceType;
    private final String pushAddress;
    private final boolean setTags;
    private final Set<String> tags;
    private final String userId;
    private final String apid;
    private final String timezone;
    private final String language;
    private final String country;


    /**
     * Builds the ChannelRegistrationPayload
     */
    static class Builder {
        private boolean optIn;
        private boolean backgroundEnabled;
        private String alias;
        private String deviceType;
        private String pushAddress;
        private boolean setTags;
        private Set<String> tags;
        private String userId;
        private String apid;
        private String timezone;
        private String language;
        private String country;

        /**
         * Set the optIn value
         *
         * @param optIn A boolean value indicating if optIn is true or false.
         * @return The builder with optIn value set
         */
        @NonNull
        Builder setOptIn(boolean optIn) {
            this.optIn = optIn;
            return this;
        }

        /**
         * Set the background enabled value.
         *
         * @param enabled enabled A boolean value indicating whether background push is enabled.
         * @return The builder with the background push enabled value set.
         */
        @NonNull
        Builder setBackgroundEnabled(boolean enabled) {
            this.backgroundEnabled = enabled;
            return this;
        }

        /**
         * Set the alias value
         *
         * @param alias A string value
         * @return The builder with alias value set
         */
        @NonNull
        Builder setAlias(String alias) {
            if (alias != null) {
                alias = alias.trim();
            }
            this.alias = UAStringUtil.isEmpty(alias) ? null : alias;
            return this;
        }

        /**
         * Set the device type
         *
         * @param deviceType A string value
         * @return The builder with device type set
         */
        @NonNull
        Builder setDeviceType(@NonNull String deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        /**
         * Set the device timezone
         *
         * @param timezone A string value of the timezone ID
         * @return The builder with timezone ID set
         */
        @NonNull
        Builder setTimezone(@NonNull String timezone) {
            this.timezone = timezone;
            return this;
        }

        /**
         * Set the device language
         *
         * @param language A string value of the language ID
         * @return The builder with language ID set
         */
        @NonNull
        Builder setLanguage(@NonNull String language) {
            this.language = language;
            return this;
        }

        /**
         * Set the device country
         *
         * @param country A string value of the country ID
         * @return The builder with country ID set
         */
        @NonNull
        Builder setCountry(@NonNull String country) {
            this.country = country;
            return this;
        }

        /**
         * Set the push address
         *
         * @param registrationId A string value
         * @return The builder with push address set
         */
        @NonNull
        Builder setPushAddress(String registrationId) {
            this.pushAddress = registrationId;
            return this;
        }

        /**
         * Set tags
         *
         * @param channelTagRegistrationEnabled A boolean value indicating whether tags are enabled on the device.
         * @param tags A set of tags
         * @return The builder with channelTagRegistrationEnabled and tags set
         */
        @NonNull
        Builder setTags(boolean channelTagRegistrationEnabled, Set<String> tags) {
            this.setTags = channelTagRegistrationEnabled;
            this.tags = tags;
            return this;
        }

        /**
         * Set the userId
         *
         * @param userId A string value
         * @return The builder with userId value set
         */
        @NonNull
        Builder setUserId(String userId) {
            this.userId = UAStringUtil.isEmpty(userId) ? null : userId;
            return this;
        }


        /**
         * Set the apid
         *
         * @param apid A string value
         * @return The builder with apid value set
         */
        @NonNull
        Builder setApid(String apid) {
            this.apid = apid;
            return this;
        }

        @NonNull
        ChannelRegistrationPayload build() {
            return new ChannelRegistrationPayload(this);
        }
    }

    private ChannelRegistrationPayload(Builder builder) {
        this.optIn = builder.optIn;
        this.backgroundEnabled = builder.backgroundEnabled;
        this.alias = builder.alias;
        this.deviceType = builder.deviceType;
        this.pushAddress = builder.pushAddress;
        this.setTags = builder.setTags;
        this.tags = builder.setTags ? builder.tags : null;
        this.userId = builder.userId;
        this.apid = builder.apid;
        this.timezone = builder.timezone;
        this.language = builder.language;
        this.country = builder.country;
    }

    @Override
    public JsonValue toJsonValue() {
        // Channel Payload
        JsonMap.Builder channel = JsonMap.newBuilder()
                                         .put(ALIAS_KEY, alias)
                                         .put(DEVICE_TYPE_KEY, deviceType)
                                         .put(SET_TAGS_KEY, setTags)
                                         .put(OPT_IN_KEY, optIn)
                                         .put(PUSH_ADDRESS_KEY, pushAddress)
                                         .put(BACKGROUND_ENABLED_KEY, backgroundEnabled)
                                         .put(TIMEZONE_KEY, timezone)
                                         .put(LANGUAGE_KEY, language)
                                         .put(COUNTRY_KEY, country);

        // If setTags is TRUE, then include the tags
        if (setTags && tags != null) {
            channel.put(TAGS_KEY, JsonValue.wrapOpt(tags).getList());
        }


        // Identity hints
        JsonMap.Builder identityHints = JsonMap.newBuilder()
                                               .put(USER_ID_KEY, userId)
                                               .put(APID_KEY, apid);


        // Full payload
        JsonMap.Builder data = JsonMap.newBuilder()
                                      .put(CHANNEL_KEY, channel.build());

        JsonMap identityHintsMap = identityHints.build();
        if (!identityHintsMap.isEmpty()) {
            data.put(IDENTITY_HINTS_KEY, identityHintsMap);
        }

        return data.build().toJsonValue();
    }

    /**
     * The ChannelRegistrationPayload as a JSON formatted string
     *
     * @return The JSON formatted payload as a string
     */
    @Override
    public String toString() {
        return this.toJsonValue().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChannelRegistrationPayload that = (ChannelRegistrationPayload) o;

        if (optIn != that.optIn) {
            return false;
        }
        if (backgroundEnabled != that.backgroundEnabled) {
            return false;
        }
        if (setTags != that.setTags) {
            return false;
        }
        if (alias != null ? !alias.equals(that.alias) : that.alias != null) {
            return false;
        }
        if (deviceType != null ? !deviceType.equals(that.deviceType) : that.deviceType != null) {
            return false;
        }
        if (pushAddress != null ? !pushAddress.equals(that.pushAddress) : that.pushAddress != null) {
            return false;
        }
        if (tags != null ? !tags.equals(that.tags) : that.tags != null) {
            return false;
        }
        if (userId != null ? !userId.equals(that.userId) : that.userId != null) {
            return false;
        }
        if (apid != null ? !apid.equals(that.apid) : that.apid != null) {
            return false;
        }
        if (timezone != null ? !timezone.equals(that.timezone) : that.timezone != null) {
            return false;
        }
        if (language != null ? !language.equals(that.language) : that.language != null) {
            return false;
        }
        return country != null ? country.equals(that.country) : that.country == null;

    }

    @Override
    public int hashCode() {
        int result = (optIn ? 1 : 0);
        result = 31 * result + (backgroundEnabled ? 1 : 0);
        result = 31 * result + (alias != null ? alias.hashCode() : 0);
        result = 31 * result + (deviceType != null ? deviceType.hashCode() : 0);
        result = 31 * result + (pushAddress != null ? pushAddress.hashCode() : 0);
        result = 31 * result + (setTags ? 1 : 0);
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        result = 31 * result + (userId != null ? userId.hashCode() : 0);
        result = 31 * result + (apid != null ? apid.hashCode() : 0);
        result = 31 * result + (timezone != null ? timezone.hashCode() : 0);
        result = 31 * result + (language != null ? language.hashCode() : 0);
        result = 31 * result + (country != null ? country.hashCode() : 0);
        return result;
    }

    /**
     * Creates a ChannelRegistrationPayload from JSON object
     *
     * @param jsonValue The JSON object to create the ChannelRegistrationPayload from
     * @return The payload as a ChannelRegistrationPayload
     */
    static ChannelRegistrationPayload parseJson(JsonValue jsonValue) throws JsonException {
        JsonMap jsonMap = jsonValue.optMap();
        JsonMap channelJson = jsonMap.opt(CHANNEL_KEY).optMap();
        JsonMap identityHints = jsonMap.opt(IDENTITY_HINTS_KEY).optMap();

        if (channelJson.isEmpty() && identityHints.isEmpty()) {
            throw new JsonException("Invalid channel payload: " + jsonValue);
        }

        Set<String> tags = new HashSet<>();
        for (JsonValue tag : channelJson.opt(TAGS_KEY).optList()) {
            if (tag.isString()) {
                tags.add(tag.getString());
            } else {
                throw new JsonException("Invalid tag: " + tag);
            }
        }

        return new Builder().setOptIn(channelJson.opt(OPT_IN_KEY).getBoolean(false))
                            .setBackgroundEnabled(channelJson.opt(BACKGROUND_ENABLED_KEY).getBoolean(false))
                            .setDeviceType(channelJson.opt(DEVICE_TYPE_KEY).getString())
                            .setPushAddress(channelJson.opt(PUSH_ADDRESS_KEY).getString())
                            .setAlias(channelJson.opt(ALIAS_KEY).getString())
                            .setLanguage(channelJson.opt(LANGUAGE_KEY).getString())
                            .setCountry(channelJson.opt(COUNTRY_KEY).getString())
                            .setTimezone(channelJson.opt(TIMEZONE_KEY).getString())
                            .setTags(channelJson.opt(SET_TAGS_KEY).getBoolean(false), tags)
                            .setUserId(identityHints.opt(USER_ID_KEY).getString())
                            .setApid(identityHints.opt(APID_KEY).getString())
                            .build();
    }
}
