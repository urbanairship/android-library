/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.config.UrlBuilder;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.http.ResponseParser;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A high level abstraction for performing Inbox API requests.
 */
public class InboxApiClient {

    private static final String USER_API_PATH = "api/user/";

    private static final String DELETE_MESSAGES_PATH = "messages/delete/";
    private static final String MARK_READ_MESSAGES_PATH = "messages/unread/";
    private static final String MESSAGES_PATH = "messages/";
    private static final String MESSAGE_PATH = "messages/message/";

    private static final String DELETE_MESSAGES_KEY = "delete";
    private static final String MARK_READ_MESSAGES_KEY = "mark_as_read";
    private static final String CHANNEL_ID_HEADER = "X-UA-Channel-ID";

    private static final String PAYLOAD_AMAZON_CHANNELS_KEY = "amazon_channels";
    private static final String PAYLOAD_ANDROID_CHANNELS_KEY = "android_channels";
    private static final String PAYLOAD_ADD_KEY = "add";

    private final AirshipRuntimeConfig runtimeConfig;
    private final RequestFactory requestFactory;

    InboxApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    InboxApiClient(@NonNull AirshipRuntimeConfig runtimeConfig, @NonNull RequestFactory requestFactory) {
        this.runtimeConfig = runtimeConfig;
        this.requestFactory = requestFactory;
    }

    @NonNull
    Response<JsonList> fetchMessages(@NonNull User user, @NonNull String channelId, long lastMessageRefreshTime) throws RequestException {
        URL url = getUserApiUrl(runtimeConfig.getUrlConfig(), user.getId(), MESSAGES_PATH);

        return requestFactory.createRequest()
                .setOperation("GET", url)
                .setCredentials(user.getId(), user.getPassword())
                .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                .setHeader(CHANNEL_ID_HEADER, channelId)
                .setIfModifiedSince(lastMessageRefreshTime)
                .execute(new ResponseParser<JsonList>() {
                    @Override
                    public JsonList parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                        if (!UAHttpStatusUtil.inSuccessRange(status)) {
                            return null;
                        }
                        JsonList messageJson =  JsonValue.parseString(responseBody).optMap().opt("messages").getList();
                        if (messageJson == null) {
                            throw new JsonException("Invalid response, missing messages.");
                        }
                        return messageJson;
                    }
                });
    }

    Response<Void> syncDeletedMessageState(@NonNull User user, @NonNull String channelId, @NonNull Set<String> idsToDelete) throws RequestException {
        AirshipUrlConfig urlConfig = runtimeConfig.getUrlConfig();
        URL url = getUserApiUrl(urlConfig, user.getId(), DELETE_MESSAGES_PATH);

        /*
         * Note: If we can't delete the messages on the server, leave them untouched
         * and we'll get them next time.
         */
        JsonMap payload = buildMessagesPayload(user.getId(), urlConfig, DELETE_MESSAGES_KEY, idsToDelete);
        Logger.verbose("InboxApiClient - Deleting inbox messages with payload: %s", payload);

        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(user.getId(), user.getPassword())
                .setRequestBody(payload.toString(), "application/json")
                .setHeader(CHANNEL_ID_HEADER, channelId)
                .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                .execute();
    }

    Response<Void> syncReadMessageState(@NonNull User user, @NonNull String channelId, @NonNull Set<String> idsToUpdate) throws RequestException {
        AirshipUrlConfig urlConfig = runtimeConfig.getUrlConfig();
        URL url = getUserApiUrl(urlConfig, user.getId(), MARK_READ_MESSAGES_PATH);

        /*
         * Note: If we can't mark the messages read on the server, leave them untouched
         * and we'll get them next time.
         */
        JsonMap payload = buildMessagesPayload(user.getId(), urlConfig, MARK_READ_MESSAGES_KEY, idsToUpdate);
        Logger.verbose("InboxApiClient - Marking inbox messages read request with payload: %s", payload);

        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(user.getId(), user.getPassword())
                .setRequestBody(payload.toString(), "application/json")
                .setHeader(CHANNEL_ID_HEADER, channelId)
                .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                .execute();
    }

    Response<UserCredentials> createUser(@NonNull String channelId) throws RequestException {
        URL url = getUserApiUrl(runtimeConfig.getUrlConfig());

        String payload = createNewUserPayload(channelId);
        Logger.verbose("InboxApiClient - Creating Rich Push user with payload: %s", payload);
        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                .setRequestBody(payload, "application/json")
                .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                .execute(new ResponseParser<UserCredentials>() {
                    @Override
                    public UserCredentials parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                        if (!UAHttpStatusUtil.inSuccessRange(status)) {
                            return null;
                        }
                        JsonMap credentials = JsonValue.parseString(responseBody).getMap();
                        if (credentials == null) {
                            throw new JsonException("InboxApiClient - Invalid response, missing credentials.");
                        }
                        String userId = credentials.opt("user_id").getString();
                        String userToken = credentials.opt("password").getString();

                        if (UAStringUtil.isEmpty(userId) || UAStringUtil.isEmpty(userToken)) {
                            throw new JsonException("InboxApiClient - Invalid response, missing credentials.");
                        }

                        return new UserCredentials(userId, userToken);
                    }
                });
    }

    Response<Void> updateUser(@NonNull User user, @NonNull String channelId) throws RequestException {
        URL url = getUserApiUrl(runtimeConfig.getUrlConfig(), user.getId());

        String payload = createUpdateUserPayload(channelId);
        Logger.verbose("InboxApiClient - Updating user with payload: %s", payload);
        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(user.getId(), user.getPassword())
                .setRequestBody(payload, "application/json")
                .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                .execute();
    }

    /**
     * Builds the message payload.
     *
     * @param urlConfig The url config.
     * @param root String root of payload.
     * @param ids Set of message ID strings.
     * @return A message payload as a JsonMap.
     */
    @NonNull
    private JsonMap buildMessagesPayload(String userId, @NonNull AirshipUrlConfig urlConfig, @NonNull String root, @NonNull Set<String> ids) {
        List<String> urls = new ArrayList<>();

        for (String id : ids) {
            URL url = getUserApiUrl(urlConfig, userId, MESSAGE_PATH, id);
            if (url != null) {
                urls.add(url.toString());
            }
        }

        JsonMap payload = JsonMap.newBuilder()
                .put(root, JsonValue.wrapOpt(urls))
                .build();

        Logger.verbose(payload.toString());
        return payload;
    }

    /**
     * Gets the URL for inbox/user api calls
     *
     * @param urlConfig The url config.
     * @param paths Additional paths.
     * @return The URL or null if an error occurred.
     */
    @Nullable
    private URL getUserApiUrl(@NonNull AirshipUrlConfig urlConfig, String... paths) {
        UrlBuilder builder = urlConfig.deviceUrl().appendEncodedPath(USER_API_PATH);

        for (String path : paths) {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            builder.appendEncodedPath(path);
        }

        return builder.build();
    }

    /**
     * Create the new user payload.
     *
     * @return The user payload as a JSON object.
     */
    private String createNewUserPayload(@NonNull String channelId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(getPayloadChannelsKey(), Collections.singletonList(channelId));
        return JsonValue.wrapOpt(payload).toString();
    }

    /**
     * Create the user update payload.
     *
     * @return The user payload as a JSON object.
     */
    private String createUpdateUserPayload(@NonNull String channelId) {
        Map<String, Object> addChannels = new HashMap<>();
        addChannels.put(PAYLOAD_ADD_KEY, Collections.singletonList(channelId));

        Map<String, Object> payload = new HashMap<>();
        payload.put(getPayloadChannelsKey(), addChannels);

        return JsonValue.wrapOpt(payload).toString();
    }

    /**
     * Get the payload channels key based on the platform.
     *
     * @return The payload channels key as a string.
     */
    @NonNull
    private String getPayloadChannelsKey() {
        if (runtimeConfig.getPlatform() == UAirship.AMAZON_PLATFORM) {
            return PAYLOAD_AMAZON_CHANNELS_KEY;
        } else {
            return PAYLOAD_ANDROID_CHANNELS_KEY;
        }
    }

}
