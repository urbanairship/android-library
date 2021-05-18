/* Copyright Airship and Contributors */

package com.urbanairship.automation.auth;

import android.net.Uri;
import android.util.Base64;

import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.http.ResponseParser;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Clock;
import com.urbanairship.util.UAHttpStatusUtil;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
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
    private final RequestFactory requestFactory;
    private final Clock clock;

    public AuthApiClient(AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, Clock.DEFAULT_CLOCK, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    AuthApiClient(AirshipRuntimeConfig runtimeConfig, Clock clock, RequestFactory requestFactory) {
        this.runtimeConfig = runtimeConfig;
        this.clock = clock;
        this.requestFactory = requestFactory;
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
        return requestFactory.createRequest()
                             .setOperation("GET", url)
                             .setAirshipJsonAcceptsHeader()
                             .setAirshipUserAgent(runtimeConfig)
                             .setHeader("X-UA-Channel-ID", channelId)
                             .setHeader("Authorization", "Bearer " + bearerToken)
                             .execute(new ResponseParser<AuthToken>() {
                                 @Override
                                 public AuthToken parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                                     if (UAHttpStatusUtil.inSuccessRange(status)) {
                                         return AuthApiClient.parseResponse(responseBody, channelId, requestTime);
                                     }
                                     return null;
                                 }
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

        String token = json.opt(TOKEN_KEY).getString();
        long expiresIn = json.opt(EXPIRES_KEY).getLong(0);

        if (token == null || expiresIn <= 0) {
            throw new JsonException("Invalid response: " + body);
        }

        return new AuthToken(channelId, token,requestTime + expiresIn);
    }
}
