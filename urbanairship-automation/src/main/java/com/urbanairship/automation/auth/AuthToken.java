/* Copyright Airship and Contributors */

package com.urbanairship.automation.auth;

import androidx.annotation.NonNull;

/**
 * Auth token data for a channel ID.
 */
class AuthToken {

    private final long expiration;
    private final String token;
    private final String channelId;

    AuthToken(@NonNull String channelId, @NonNull String token, long expiration) {
        this.token = token;
        this.expiration = expiration;
        this.channelId = channelId;
    }

    /**
     * Expiration in milliseconds.
     *
     * @return The token expiration date (unix epoch time in milliseconds).
     */
    public long getExpiration() {
        return expiration;
    }

    /**
     * Gets the bearer token.
     *
     * @return The bearer token.
     */
    @NonNull
    public String getToken() {
        return token;
    }

    /**
     * The token's channel ID.
     *
     * @return The token's channel ID.
     */
    @NonNull
    public String getChannelId() {
        return channelId;
    }

}
