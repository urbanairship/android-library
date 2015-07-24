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

package com.urbanairship.richpush;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.util.UAStringUtil;

import java.io.UnsupportedEncodingException;

/**
 * The preferences for the Rich Push User.
 */
class RichPushUserPreferences {

    private static final String KEY_PREFIX = "com.urbanairship.user";
    private static final String USER_ID_KEY = KEY_PREFIX + ".ID";
    private static final String USER_PASSWORD_KEY = KEY_PREFIX + ".PASSWORD";
    private static final String USER_TOKEN_KEY = KEY_PREFIX + ".USER_TOKEN";
    private static final String LAST_UPDATE_TIME = KEY_PREFIX + ".LAST_UPDATE_TIME";
    private static final String LAST_MESSAGE_REFRESH_TIME = KEY_PREFIX + ".LAST_MESSAGE_REFRESH_TIME";

    private final PreferenceDataStore preferenceDataStore;

    RichPushUserPreferences(PreferenceDataStore preferenceDataStore) {
        this.preferenceDataStore = preferenceDataStore;

        String password = preferenceDataStore.getString(USER_PASSWORD_KEY, null);

        if (!UAStringUtil.isEmpty(password)) {
            String userToken = encode(password, preferenceDataStore.getString(USER_ID_KEY, null));

            if (preferenceDataStore.putSync(USER_TOKEN_KEY, userToken)) {
                preferenceDataStore.remove(USER_PASSWORD_KEY);
            }
        }
    }

    /**
     * Set the user ID and user token.
     *
     * @param userId The user ID string.
     * @param userToken The user token string.
     */
    public void setUserCredentials(String userId, String userToken) {
        preferenceDataStore.put(USER_ID_KEY, userId);
        preferenceDataStore.put(USER_TOKEN_KEY, encode(userToken, userId));
    }

    /**
     * Get the user ID.
     *
     * @return The user ID string.
     */
    public String getUserId() {
        return preferenceDataStore.getString(USER_ID_KEY, null);
    }

    /**
     * Get the user's token used for basic auth.
     *
     * @return The user token string.
     */
    public String getUserToken() {
        return decode(preferenceDataStore.getString(USER_TOKEN_KEY, null), getUserId());
    }

    /**
     * Get the last message refresh time.
     *
     * @return The last message refresh time.
     */
    public long getLastMessageRefreshTime() {
        return preferenceDataStore.getLong(LAST_MESSAGE_REFRESH_TIME, 0);
    }

    /**
     * Set the last message refresh time.
     *
     * @param timeMs The time in milliseconds to set.
     */
    public void setLastMessageRefreshTime(long timeMs) {
        preferenceDataStore.put(LAST_MESSAGE_REFRESH_TIME, timeMs);
    }

    /**
     * Get the last update time.
     *
     * @return The last update time in milliseconds.
     */
    public long getLastUpdateTime() {
        return preferenceDataStore.getLong(LAST_UPDATE_TIME, 0);
    }

    /**
     * Set the last update time.
     *
     * @param timeMs The time in milliseconds.
     */
    public void setLastUpdateTime(long timeMs) {
        preferenceDataStore.put(LAST_UPDATE_TIME, timeMs);
    }

    /**
     * Encode the string with the key.
     *
     * @param input The string to encode.
     * @param key The key used to encode the string.
     * @return The encoded string.
     */
    private String encode(String input, String key) {
        if (UAStringUtil.isEmpty(input) || UAStringUtil.isEmpty(key)) {
            return null;
        }

        // xor the two strings together
        byte[] bytes = xor(input.getBytes(), key.getBytes());

        // Format the raw byte array as a hex string
        StringBuilder hexHash = new StringBuilder();
        for (byte b : bytes) {
            hexHash.append(String.format("%02x", b));
        }
        return hexHash.toString();
    }

    /**
     * Decode the string with the key.
     *
     * @param encodedString The string to decode.
     * @param key The key used to decode the string.
     * @return The decoded string.
     */
    private String decode(String encodedString, String key) {

        if (UAStringUtil.isEmpty(encodedString) || UAStringUtil.isEmpty(key)) {
            return null;
        }

        int length = encodedString.length();

        // Make sure we have an even number of chars
        if (length % 2 != 0) {
            return null;
        }

        try {
            // Decode the encodedString to a byte array
            byte[] decodedBytes = new byte[length / 2];
            for (int i = 0; i < length; i += 2) {
                decodedBytes[i / 2] = Byte.parseByte(encodedString.substring(i, i + 2), 16);
            }
            decodedBytes = xor(decodedBytes, key.getBytes());

            return new String(decodedBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Logger.error("RichPushUserPreferences - Unable to decode string. " + e.getMessage());
        } catch (NumberFormatException e) {
            Logger.error("RichPushUserPreferences - String contains invalid hex numbers. " + e.getMessage());
        }

        return null;
    }

    /**
     * Compare and return the xor value.
     *
     * @param a The byte value.
     * @param b The byte value.
     * @return The byte result value.
     */
    private byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];

        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i % b.length]);
        }

        return out;
    }
}
