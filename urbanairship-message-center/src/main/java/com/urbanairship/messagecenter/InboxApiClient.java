/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.net.Uri;

import com.urbanairship.UALog;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.config.UrlBuilder;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestAuth;
import com.urbanairship.http.RequestBody;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestSession;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A high level abstraction for performing Inbox API requests.
 */
public class InboxApiClient {

    private static final String USER_API_PATH = "api/user/";

    private static final String DELETE_MESSAGES_PATH = "messages/delete/";
    private static final String MARK_READ_MESSAGES_PATH = "messages/unread/";
    private static final String MESSAGES_PATH = "messages/";

    private static final String MESSAGES_REPORTINGS_KEY = "messages";
    private static final String CHANNEL_ID_HEADER = "X-UA-Channel-ID";

    private static final String PAYLOAD_AMAZON_CHANNELS_KEY = "amazon_channels";
    private static final String PAYLOAD_ANDROID_CHANNELS_KEY = "android_channels";
    private static final String PAYLOAD_ADD_KEY = "add";

    private final AirshipRuntimeConfig runtimeConfig;
    private final RequestSession session;

    InboxApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, runtimeConfig.getRequestSession());
    }

    InboxApiClient(@NonNull AirshipRuntimeConfig runtimeConfig, @NonNull RequestSession session) {
        this.runtimeConfig = runtimeConfig;
        this.session = session;
    }

    @NonNull
    Response<JsonList> fetchMessages(@NonNull User user, @NonNull String channelId, @Nullable String ifModifiedSince) throws RequestException {
        Uri url = getUserApiUrl(runtimeConfig.getUrlConfig(), user.getId(), MESSAGES_PATH);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");
        headers.put(CHANNEL_ID_HEADER, channelId);

        if (ifModifiedSince != null) {
            headers.put("If-Modified-Since", ifModifiedSince);
        }

        Request request = new Request(
                url,
                "GET",
                getUserAuth(user),
                null,
                headers
        );

        return session.execute(request, (status, responseHeaders, responseBody) -> {
            if (!UAHttpStatusUtil.inSuccessRange(status)) {
                return null;
            }
            return JsonValue.parseString(responseBody).optMap().opt("messages").requireList();
        });
    }

    Response<Void> syncDeletedMessageState(@NonNull User user, @NonNull String channelId, @NonNull List<JsonValue> reportingsToDelete) throws RequestException {
        AirshipUrlConfig urlConfig = runtimeConfig.getUrlConfig();
        Uri url = getUserApiUrl(urlConfig, user.getId(), DELETE_MESSAGES_PATH);

        JsonMap payload = JsonMap.newBuilder()
                                 .put(MESSAGES_REPORTINGS_KEY, JsonValue.wrapOpt(reportingsToDelete))
                                 .build();

        UALog.v("Deleting inbox messages with payload: %s", payload);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");
        headers.put(CHANNEL_ID_HEADER, channelId);

        Request request = new Request(
                url,
                "POST",
                getUserAuth(user),
                new RequestBody.Json(payload),
                headers
        );

        return session.execute(request, (status, responseHeaders, responseBody) -> null);
    }

    Response<Void> syncReadMessageState(@NonNull User user, @NonNull String channelId, @NonNull List<JsonValue> reportingsToUpdate) throws RequestException {
        AirshipUrlConfig urlConfig = runtimeConfig.getUrlConfig();
        Uri url = getUserApiUrl(urlConfig, user.getId(), MARK_READ_MESSAGES_PATH);

        JsonMap payload = JsonMap.newBuilder()
                                 .put(MESSAGES_REPORTINGS_KEY, JsonValue.wrapOpt(reportingsToUpdate))
                                 .build();

        UALog.v("Marking inbox messages read request with payload: %s", payload);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");
        headers.put(CHANNEL_ID_HEADER, channelId);

        Request request = new Request(
                url,
                "POST",
                getUserAuth(user),
                new RequestBody.Json(payload),
                headers
        );

        return session.execute(request, (status, responseHeaders, responseBody) -> null);
    }

    Response<UserCredentials> createUser(@NonNull String channelId) throws RequestException {
        Uri url = getUserApiUrl(runtimeConfig.getUrlConfig());

        JsonMap payload = JsonMap.newBuilder()
                                 .putOpt(getPayloadChannelsKey(), Collections.singletonList(channelId))
                                 .build();

        UALog.v("Creating Rich Push user with payload: %s", payload);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");
        headers.put(CHANNEL_ID_HEADER, channelId);

        Request request = new Request(
                url,
                "POST",
                RequestAuth.BasicAppAuth.INSTANCE,
                new RequestBody.Json(payload),
                headers
        );

        return session.execute(request, (status, responseHeaders, responseBody) -> {
            if (!UAHttpStatusUtil.inSuccessRange(status)) {
                return null;
            }
            JsonMap credentials = JsonValue.parseString(responseBody).requireMap();
            String userId = credentials.opt("user_id").requireString();
            String userToken = credentials.opt("password").requireString();
            return new UserCredentials(userId, userToken);
        });
    }

    Response<Void> updateUser(@NonNull User user, @NonNull String channelId) throws RequestException {
        Uri url = getUserApiUrl(runtimeConfig.getUrlConfig(), user.getId());

        JsonMap payload = JsonMap.newBuilder()
                                 .putOpt(
                                         getPayloadChannelsKey(),
                                         JsonMap.newBuilder()
                                                .putOpt(PAYLOAD_ADD_KEY, Collections.singletonList(channelId))
                                                .build()
                                 )
                                 .build();

        UALog.v("Updating user with payload: %s", payload);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");
        headers.put(CHANNEL_ID_HEADER, channelId);

        Request request = new Request(
                url,
                "POST",
                getUserAuth(user),
                new RequestBody.Json(payload),
                headers
        );

        return session.execute(request, (status, responseHeaders, responseBody) -> null);
    }

    /**
     * Gets the URL for inbox/user api calls
     *
     * @param urlConfig The url config.
     * @param paths Additional paths.
     * @return The URL or null if an error occurred.
     */
    @Nullable
    private Uri getUserApiUrl(@NonNull AirshipUrlConfig urlConfig, String... paths) {
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
     * Get the payload channels key based on the platform.
     *
     * @return The payload channels key as a string.
     */
    @NonNull
    private String getPayloadChannelsKey() throws RequestException {
        switch (runtimeConfig.getPlatform()) {
            case UAirship.AMAZON_PLATFORM:
                return PAYLOAD_AMAZON_CHANNELS_KEY;
            case UAirship.ANDROID_PLATFORM:
                return PAYLOAD_ANDROID_CHANNELS_KEY;
            default:
                throw new RequestException("Invalid platform");
        }
    }

    private RequestAuth getUserAuth(@NonNull User user) throws RequestException {
        String userId = user.getId();
        String userPassword = user.getPassword();
        if (userId == null || userPassword == null) {
            throw new RequestException("Missing user credentials");
        }
        return new RequestAuth.BasicAuth(userId, userPassword);
    }

}
