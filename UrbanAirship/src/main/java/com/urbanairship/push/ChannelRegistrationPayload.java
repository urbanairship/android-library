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

import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Model object encapsulating the data relevant to a creation or updates processed by ChannelAPIClient.
 */
class ChannelRegistrationPayload {
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
        Builder setDeviceType(String deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        /**
         * Set the push address
         *
         * @param registrationId A string value
         * @return The builder with push address set
         */
        Builder setPushAddress(String registrationId) {
            this.pushAddress = registrationId;
            return this;
        }

        /**
         * Set tags
         *
         * @param deviceTagsEnabled A boolean value indicating whether tags are enabled on the device.
         * @param tags A set of tags
         * @return The builder with deviceTagsEnabled and tags set
         */
        Builder setTags(boolean deviceTagsEnabled, Set<String> tags) {
            this.setTags = deviceTagsEnabled;
            this.tags = tags;
            return this;
        }

        /**
         * Set the userId
         *
         * @param userId A string value
         * @return The builder with userId value set
         */
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
        Builder setApid(String apid) {
            this.apid = apid;
            return this;
        }

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

    /**
     * The ChannelRegistrationPayload as JSON data.
     *
     * @return The payload as JSON data.
     */
    JSONObject asJSON() {

        JSONObject payload = new JSONObject();
        JSONObject channel = new JSONObject();
        JSONObject identityHints = new JSONObject();

        try {
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
                channel.put(TAGS_KEY, new JSONArray(tags));
            }
            payload.put(CHANNEL_KEY, channel);

            if (!UAStringUtil.isEmpty(userId)) {
                identityHints.put(USER_ID_KEY, userId);
            }

            if (!UAStringUtil.isEmpty(apid)) {
                identityHints.put(APID_KEY, apid);
            }

            if (identityHints.length() != 0) {
                payload.put(IDENTITY_HINTS_KEY, identityHints);
            }

        } catch (Exception ex) {
            Logger.error("ChannelRegistrationPayload - Failed to create channel registration payload as json", ex);
        }

        return payload;
    }

    /**
     * The ChannelRegistrationPayload as a JSON formatted string
     *
     * @return The JSON formatted payload as a string
     */
    @Override
    public String toString() {
        return this.asJSON().toString();
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
     * @param json The JSON object to create the ChannelRegistrationPayload from
     * @return The payload as a ChannelRegistrationPayload
     */
    protected static ChannelRegistrationPayload createFromJSON(JSONObject json) {
        Builder builder = new Builder();
        if (json == null || json.length() == 0) {
            return null;
        }

        try {
            JSONObject channelJSON = json.getJSONObject(CHANNEL_KEY);
            builder.setOptIn(channelJSON.getBoolean(OPT_IN_KEY))
                   .setBackgroundEnabled(channelJSON.getBoolean(BACKGROUND_ENABLED_KEY))
                   .setDeviceType(getStringFromJSON(channelJSON, DEVICE_TYPE_KEY))
                   .setPushAddress(getStringFromJSON(channelJSON, PUSH_ADDRESS_KEY))
                   .setAlias(getStringFromJSON(channelJSON, ALIAS_KEY))
                   .setUserId(getStringFromJSON(channelJSON, USER_ID_KEY))
                   .setApid(getStringFromJSON(channelJSON, APID_KEY));

            boolean deviceTagsEnabled = false;
            Set<String> tags = null;

            if (channelJSON.has(TAGS_KEY)) {
                JSONArray tagsJSON = channelJSON.getJSONArray(TAGS_KEY);
                tags = new HashSet<>();
                for (int i = 0; i < tagsJSON.length(); i++) {
                    tags.add(tagsJSON.getString(i));
                }
            }

            if (channelJSON.has(SET_TAGS_KEY)) {
                deviceTagsEnabled = channelJSON.getBoolean(SET_TAGS_KEY);
            }

            builder.setTags(deviceTagsEnabled, tags);

            if (json.has(IDENTITY_HINTS_KEY)) {
                JSONObject identityHintsJSON = json.getJSONObject(IDENTITY_HINTS_KEY);
                builder.setUserId(getStringFromJSON(identityHintsJSON, USER_ID_KEY))
                       .setApid(getStringFromJSON(identityHintsJSON, APID_KEY));
            }
        } catch (JSONException e) {
            Logger.error("ChannelRegistrationPayload - Failed to parse payload from JSON.", e);
            return null;
        }

        return builder.build();
    }

    /**
     * Get the string value from the JSON object
     *
     * @param json The JSON object to get the value from
     * @param key The string key specified
     * @return The string value from the JSON object
     */
    private static String getStringFromJSON(JSONObject json, String key) {
        try {
            return json.has(key) ? json.getString(key) : null;
        } catch (JSONException e) {
            return null;
        }
    }
}
