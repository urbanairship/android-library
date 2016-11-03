/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Base class for the NamedUser and Channel Api Clients.
 */
abstract class BaseApiClient {

    private static final String AUDIENCE_KEY = "audience";
    private static final String ADD_KEY = "add";
    private static final String REMOVE_KEY = "remove";
    private static final String SET_KEY = "set";

    private final AirshipConfigOptions configOptions;
    private final RequestFactory requestFactory;

    BaseApiClient(@NonNull AirshipConfigOptions configOptions, @NonNull RequestFactory requestFactory) {
        this.requestFactory = requestFactory;
        this.configOptions = configOptions;
    }

    /**
     * Update the tag groups for the given identifier.
     *
     * @param audienceId The audienceId.
     * @param addTags The map of tags to add.
     * @param removeTags The map of tags to remove.
     * @param setTags The map of tags to set.
     * @return The response or null if an error occurred.
     */
    Response updateTagGroups(@NonNull String audienceId,
                             Map<String, Set<String>> addTags,
                             Map<String, Set<String>> removeTags,
                             Map<String, Set<String>> setTags) {

        URL tagUrl = getDeviceUrl(getTagGroupPath());
        if (tagUrl == null) {
            Logger.error("Invalid tag URL. Unable to update tagGroups.");
            return null;
        }

        if ((addTags != null && addTags.isEmpty()) && (removeTags != null && removeTags.isEmpty())
                && (setTags != null && setTags.isEmpty())) {
            Logger.error("addTags, removeTags, and setTags cannot all be empty.");
            return null;
        }

        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> audience = new HashMap<>();

        audience.put(getTagGroupAudienceSelector(), audienceId);
        payload.put(AUDIENCE_KEY, audience);
        if (setTags != null && !setTags.isEmpty()) {
            payload.put(SET_KEY, setTags);
        }
        if (addTags != null && !addTags.isEmpty()) {
            payload.put(ADD_KEY, addTags);
        }
        if (removeTags != null && !removeTags.isEmpty()) {
            payload.put(REMOVE_KEY, removeTags);
        }

        String tagPayload = JsonValue.wrapOpt(payload).toString();
        Logger.info("Updating tag groups with payload: " + tagPayload);

        Response response = performRequest(getDeviceUrl(getTagGroupPath()), "POST", tagPayload);
        logTagGroupResponseIssues(response);

        return response;
    }

    /**
     * Tag group audience selector.
     *
     * @return Tag group audience selector.
     */
    protected abstract String getTagGroupAudienceSelector();

    /**
     * Tag group path.
     *
     * @return The tag group API path.
     */
    protected abstract String getTagGroupPath();

    /**
     * Log the response warnings and errors if they exist in the response body.
     *
     * @param response The tag group response.
     */
    private void logTagGroupResponseIssues(Response response) {
        if (response == null || response.getResponseBody() == null) {
            return;
        }

        String responseBody = response.getResponseBody();

        JsonValue responseJson;
        try {
            responseJson = JsonValue.parseString(responseBody);
        } catch (JsonException e) {
            Logger.error("Unable to parse tag group response", e);
            return;
        }

        if (responseJson.isJsonMap()) {
            // Check for any warnings in the response and log them if they exist.
            if (responseJson.getMap().containsKey("warnings")) {
                for (JsonValue warning : responseJson.getMap().get("warnings").getList()) {
                    Logger.warn("Tag Groups warnings: " + warning);
                }
            }

            // Check for any errors in the response and log them if they exist.
            if (responseJson.getMap().containsKey("error")) {
                Logger.error("Tag Groups error: " + responseJson.getMap().get("error"));
            }
        }
    }

    /**
     * Performs a request.
     *
     * @param url The specified URL to send the request to.
     * @param requestMethod String representing the request method to use.
     * @param jsonPayload JSON payload as a string
     * @return response or null if an error occurred
     */
    protected Response performRequest(@Nullable URL url, @NonNull String requestMethod, @NonNull String jsonPayload) {
        if (url == null) {
            Logger.error("Unable to perform request, invalid URL.");
            return null;
        }

        return requestFactory.createRequest(requestMethod, url)
                             .setCredentials(configOptions.getAppKey(), configOptions.getAppSecret())
                             .setRequestBody(jsonPayload, "application/json")
                             .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                             .execute();
    }

    /**
     * Gets a device url for a given path.
     *
     * @param path The api path.
     * @return The device URL or {@code null} if the URL is invalid.
     */
    @Nullable
    protected URL getDeviceUrl(@NonNull String path) {
        try {
            return new URL(configOptions.hostURL + path);
        } catch (MalformedURLException e) {
            Logger.error("Invalid URL: " + path, e);
            return null;
        }
    }
}
