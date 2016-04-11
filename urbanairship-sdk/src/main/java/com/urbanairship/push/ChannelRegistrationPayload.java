/*
Copyright 2009-2013 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.push;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    private final boolean optIn;
    private final boolean backgroundEnabled;
    private final String alias;
    private final String deviceType;
    private final String pushAddress;
    private final boolean setTags;
    private final Set<String> tags;
    private final String userId;
    private final String apid;


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
            this.alias = alias;
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
            this.userId = userId;
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
    }

    @Override
    public JsonValue toJsonValue() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> identityHints = new HashMap<>();
        Map<String, Object> channel = new HashMap<>();

        // Channel
        channel.put(DEVICE_TYPE_KEY, deviceType);
        channel.put(OPT_IN_KEY, optIn);
        channel.put(BACKGROUND_ENABLED_KEY, backgroundEnabled);
        channel.put(PUSH_ADDRESS_KEY, pushAddress);

        if (!UAStringUtil.isEmpty(alias)) {
            channel.put(ALIAS_KEY, alias);
        }

        channel.put(SET_TAGS_KEY, setTags);

        // If setTags is TRUE, then include the tags
        if (setTags && tags != null) {
            channel.put(TAGS_KEY, JsonValue.wrapOpt(tags).getList());
        }

        payload.put(CHANNEL_KEY, channel);

        // Identity hints
        if (!UAStringUtil.isEmpty(userId)) {
            identityHints.put(USER_ID_KEY, userId);
        }

        if (!UAStringUtil.isEmpty(apid)) {
            identityHints.put(APID_KEY, apid);
        }

        if (!identityHints.isEmpty()) {
            payload.put(IDENTITY_HINTS_KEY, identityHints);
        }

        try {
            return JsonValue.wrap(payload);
        } catch (JsonException e) {
            Logger.error("ChannelRegistrationPayload - Failed to create channel registration payload as json", e);
            return JsonValue.NULL;
        }
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

    /**
     * Compares this instance with the specified object and indicates if they are equal.
     *
     * @param o The object to compare this instance with.
     * @return <code>true</code>if objects are equal, <code>false</code> otherwise
     */
    @Override
    public boolean equals(Object o) {

        // Return false if the object is null or has the wrong type
        if (o == null || !(o instanceof ChannelRegistrationPayload)) {
            return false;
        }

        // Cast to the appropriate type.
        ChannelRegistrationPayload lhs = (ChannelRegistrationPayload) o;

        // Check each field
        return ((optIn == lhs.optIn) &&
                (backgroundEnabled == lhs.backgroundEnabled) &&
                (alias == null ? lhs.alias == null : alias.equals(lhs.alias)) &&
                (deviceType == null ? lhs.deviceType == null : deviceType.equals(lhs.deviceType)) &&
                (pushAddress == null ? lhs.pushAddress == null : pushAddress.equals(lhs.pushAddress)) &&
                (setTags == lhs.setTags) &&
                (tags == null ? lhs.tags == null : tags.equals(lhs.tags)) &&
                (userId == null ? lhs.userId == null : userId.equals(lhs.userId)) &&
                (apid == null ? lhs.apid == null : apid.equals(lhs.apid)));
    }

    /**
     * Returns an integer hash code for this object.
     *
     * @return This object's hash code.
     */
    @Override
    public int hashCode() {
        // Start with a non-zero constant.
        int result = 17;

        // Include a hash for each field.
        result = 31 * result + (optIn ? 1 : 0);
        result = 31 * result + (backgroundEnabled ? 1 : 0);
        result = 31 * result + (alias == null ? 0 : alias.hashCode());
        result = 31 * result + (deviceType == null ? 0 : deviceType.hashCode());
        result = 31 * result + (pushAddress == null ? 0 : pushAddress.hashCode());
        result = 31 * result + (setTags ? 1 : 0);
        result = 31 * result + (tags == null ? 0 : tags.hashCode());
        result = 31 * result + (userId == null ? 0 : userId.hashCode());
        result = 31 * result + (apid == null ? 0 : apid.hashCode());

        return result;
    }

    /**
     * Creates a ChannelRegistrationPayload from JSON object
     *
     * @param jsonString The JSON object to create the ChannelRegistrationPayload from
     * @return The payload as a ChannelRegistrationPayload
     */
    static ChannelRegistrationPayload parseJson(String jsonString) throws JsonException {
        JsonMap jsonMap = JsonValue.parseString(jsonString).getMap();
        if (jsonMap == null || jsonMap.isEmpty()) {
            return null;
        }

        Builder builder = new Builder();

        JsonMap channelJSON = jsonMap.opt(CHANNEL_KEY).getMap();
        if (channelJSON != null) {
            builder.setOptIn(channelJSON.opt(OPT_IN_KEY).getBoolean(false))
                    .setBackgroundEnabled(channelJSON.opt(BACKGROUND_ENABLED_KEY).getBoolean(false))
                    .setDeviceType(channelJSON.opt(DEVICE_TYPE_KEY).getString())
                    .setPushAddress(channelJSON.opt(PUSH_ADDRESS_KEY).getString())
                    .setAlias(channelJSON.opt(ALIAS_KEY).getString())
                    .setUserId(channelJSON.opt(USER_ID_KEY).getString())
                    .setApid(channelJSON.opt(APID_KEY).getString());

            Set<String> tags = null;
            if (channelJSON.opt(TAGS_KEY).isJsonList()) {
                tags = new HashSet<>();
                for (JsonValue tag : channelJSON.get(TAGS_KEY).getList()) {
                    if (tag.isString()) {
                        tags.add(tag.getString());
                    }
                }
            }

            builder.setTags(channelJSON.opt(SET_TAGS_KEY).getBoolean(false), tags);
        }

        JsonMap identityHints = jsonMap.opt(IDENTITY_HINTS_KEY).getMap();

        if (identityHints != null) {
            builder.setUserId(identityHints.opt(USER_ID_KEY).getString())
                    .setApid(identityHints.opt(APID_KEY).getString());
        }

        return builder.build();

    }
}
