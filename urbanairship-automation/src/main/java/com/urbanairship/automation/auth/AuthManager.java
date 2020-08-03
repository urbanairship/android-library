/* Copyright Airship and Contributors */

package com.urbanairship.automation.auth;

import com.urbanairship.Logger;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.util.Clock;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * Auth manager.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AuthManager {

    private final Object cachedAuthLock = new Object();
    private final AuthApiClient apiClient;
    private final AirshipChannel channel;
    private final Clock clock;

    private AuthToken cachedAuth;

    public AuthManager(@NonNull AirshipRuntimeConfig runtimeConfig, @NonNull AirshipChannel channel) {
        this(new AuthApiClient(runtimeConfig), channel, Clock.DEFAULT_CLOCK);
    }

    @VisibleForTesting
    AuthManager(@NonNull AuthApiClient apiClient, @NonNull AirshipChannel channel, @NonNull Clock clock) {
        this.apiClient = apiClient;
        this.channel = channel;
        this.clock = clock;
    }

    /**
     * Gets the auth token.
     *
     * @return The auth token or null if the token was unable to be requested.
     */
    @WorkerThread
    @Nullable
    public String getToken() {
        String channelId = channel.getId();

        if (channelId == null) {
            return null;
        }

        String cachedToken = getCachedToken(channelId);
        if (cachedToken != null) {
            return cachedToken;
        }

        try {
            Response<AuthToken> authResponse = apiClient.getToken(channelId);
            if (authResponse.getResult() != null && authResponse.isSuccessful()) {
                cache(authResponse.getResult());
                return authResponse.getResult().getToken();
            } else {
                Logger.error("Failed to generate token: %s", authResponse);
            }
        } catch (RequestException e) {
            Logger.error(e, "Failed to generate token");
        }

        return null;
    }

    /**
     * Called to clear a token early if its expired.
     *
     * @param token The expired token.
     */
    public void tokenExpired(@NonNull String token) {
        synchronized (cachedAuthLock) {
            if (token.equals(cachedAuth.getToken())) {
                cachedAuth = null;
            }
        }
    }

    private void cache(AuthToken cachedAuth) {
        synchronized (cachedAuthLock) {
            this.cachedAuth = cachedAuth;
        }
    }

    @Nullable
    private String getCachedToken(@NonNull String channelId) {
        synchronized (cachedAuthLock) {
            if (cachedAuth == null) {
                return null;
            }

            if (clock.currentTimeMillis() >= cachedAuth.getExpiration()) {
                return null;
            }

            if (!UAStringUtil.equals(channelId, cachedAuth.getChannelId())) {
                return null;
            }

            return cachedAuth.getToken();
        }
    }

}
