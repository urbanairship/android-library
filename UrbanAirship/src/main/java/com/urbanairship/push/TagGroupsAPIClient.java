/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

/**
 * A high level abstraction for performing tag groups operations.
 */
class TagGroupsAPIClient {

    static final String CHANNEL_TAGS_PATH = "/api/channels/tags/";
    static final String NAMED_USER_TAGS_PATH = "/api/named_users/tags/";
    static final String AUDIENCE_KEY = "audience";
    static final String ANDROID_CHANNEL_KEY = "android_channel";
    static final String AMAZON_CHANNEL_KEY = "amazon_channel";
    static final String NAMED_USER_ID_KEY = "named_user_id";
    static final String ADD_KEY = "add";
    static final String REMOVE_KEY = "remove";

    protected String urlString;
    private RequestFactory requestFactory;

    TagGroupsAPIClient() {
        this(new RequestFactory());
    }

    public TagGroupsAPIClient(RequestFactory requestFactory) {
        this.requestFactory = requestFactory;
        this.urlString = UAirship.shared().getAirshipConfigOptions().hostURL;
    }

    Response updateNamedUserTags(String namedUserId,
                                 Map<String, Set<String>> addTags,
                                 Map<String, Set<String>> removeTags) {

        if (UAStringUtil.isEmpty(namedUserId)) {
            Logger.error("The named user ID cannot be null.");
            return null;
        }

        JSONObject payload = new JSONObject();
        JSONObject audience = new JSONObject();

        try {
            audience.put(NAMED_USER_ID_KEY, namedUserId);
            payload.put(AUDIENCE_KEY, audience);
            payload.put(ADD_KEY, addTags);
            payload.put(REMOVE_KEY, removeTags);

        } catch (JSONException e) {
            Logger.error("Failed to create named user tags payload as json.", e);
        }

        URL namedUserTagsUrl;
        String urlString = this.urlString + NAMED_USER_TAGS_PATH;
        try {
            namedUserTagsUrl = new URL(urlString);
        } catch (MalformedURLException e) {
            Logger.error("Invalid named user tags URL.", e);
            return null;
        }

        return request(namedUserTagsUrl, payload.toString());
    }

    Response updateChannelTags(String channelId,
                               Map<String, Set<String>> addTags,
                               Map<String, Set<String>> removeTags) {

        if (UAStringUtil.isEmpty(channelId)) {
            Logger.error("The channel ID cannot be null.");
            return null;
        }

        JSONObject payload = new JSONObject();
        JSONObject audience = new JSONObject();

        try {
            audience.put(getChannelType(), channelId);
            payload.put(AUDIENCE_KEY, audience);
            payload.put(ADD_KEY, addTags);
            payload.put(REMOVE_KEY, removeTags);

        } catch (JSONException e) {
            Logger.error("Failed to create channel tag groups payload as json.", e);
        }

        URL channelTagsUrl;
        String urlString = this.urlString + CHANNEL_TAGS_PATH;
        try {
            channelTagsUrl = new URL(urlString);
        } catch (MalformedURLException e) {
            Logger.error("Invalid channel tag groups URL.", e);
            return null;
        }

        return request(channelTagsUrl, payload.toString());
    }

    /**
     * Sends the tag groups request.
     *
     * @param url The specified URL to send the request to.
     * @param jsonPayload The JSON payload as a string.
     * @return The response or null if an error occurred.
     */
    private Response request(URL url, String jsonPayload) {
        String appKey = UAirship.shared().getAirshipConfigOptions().getAppKey();
        String appSecret = UAirship.shared().getAirshipConfigOptions().getAppSecret();

        Response response = requestFactory.createRequest("POST", url)
                                          .setCredentials(appKey, appSecret)
                                          .setRequestBody(jsonPayload, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        if (response == null) {
            Logger.error("Failed to receive a response for tag groups.");
        } else {
            Logger.debug("Received a response for tag groups: " + response);
        }

        return response;
    }

    /**
     * Returns the channel type based on the platform.
     *
     * @return The channel type string.
     */
    String getChannelType() {
        String channelType = null;
        switch (UAirship.shared().getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                channelType = ANDROID_CHANNEL_KEY;
                break;
            case UAirship.AMAZON_PLATFORM:
                channelType = AMAZON_CHANNEL_KEY;
                break;
        }
        return channelType;
    }
}
