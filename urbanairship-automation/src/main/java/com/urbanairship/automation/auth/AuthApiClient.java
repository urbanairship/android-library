/* Copyright Airship and Contributors */

package com.urbanairship.automation.auth;

import android.net.Uri;
import android.util.Base64;

import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestAuth;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestSession;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Clock;
import com.urbanairship.util.UAHttpStatusUtil;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;


class AuthApiClient {
    private static final String AUTH_PATH = "api/auth/device";

    private static final String TOKEN_KEY = "token";
    private static final String EXPIRES_KEY = "expires_in";

    private final AirshipRuntimeConfig runtimeConfig;
    private final RequestSession session;
    private final Clock clock;

    public AuthApiClient(AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, runtimeConfig.getRequestSession(), Clock.DEFAULT_CLOCK);
    }

    @VisibleForTesting
    AuthApiClient(AirshipRuntimeConfig runtimeConfig, RequestSession session, Clock clock) {
        this.runtimeConfig = runtimeConfig;
        this.clock = clock;
        this.session = session;
    }

    @NonNull
    public Response<AuthToken> getToken(@NonNull final String channelId) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(AUTH_PATH)
                               .build();

        String bearerToken;
        try {
            bearerToken = createBearerToken(channelId);
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException e) {
          throw new RequestException("Unable to create bearer token.", e);
        }

        final long requestTime = clock.currentTimeMillis();
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");
        headers.put("X-UA-Channel-ID", channelId);

        Request request = new Request(
                url,
                "GET",
                new RequestAuth.BearerToken(bearerToken),
                null,
                headers
        );

        return session.execute(request, (status, responseHeaders, responseBody) -> {
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                return AuthApiClient.parseResponse(responseBody, channelId, requestTime);
            }
            return null;
        });
    }

    @Nullable
    private String createBearerToken(@NonNull String channelId) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret = new SecretKeySpec(runtimeConfig.getConfigOptions().appSecret.getBytes("UTF-8"), "HmacSHA256");
        hmac.init(secret);
        String message = runtimeConfig.getConfigOptions().appKey + ":" + channelId;
        byte[] hashed = hmac.doFinal(message.getBytes("UTF-8"));
        return Base64.encodeToString(hashed, Base64.DEFAULT);
    }

    private static AuthToken parseResponse(@Nullable String body, @NonNull String channelId, long requestTime) throws JsonException {
        JsonMap json = JsonValue.parseString(body).optMap();
        String token = json.opt(TOKEN_KEY).requireString();
        long expiresIn = json.opt(EXPIRES_KEY).getLong(0);
        if (expiresIn <= 0) {
            throw new JsonException("Invalid response: " + body);
        }
        return new AuthToken(channelId, token,requestTime + expiresIn);
    }
}
