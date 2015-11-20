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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Urban Airship rich push user.
 */
public class RichPushUser {

    /**
     * A listener interface for receiving events for user updates.
     */
    public interface Listener {

        /**
         * Called when the user is updated.
         *
         * @param success {@code} if the request was successful, otherwise {@code false}.
         */
        void onUserUpdated(boolean success);
    }

    private static final String KEY_PREFIX = "com.urbanairship.user";
    private static final String USER_ID_KEY = KEY_PREFIX + ".ID";
    private static final String USER_PASSWORD_KEY = KEY_PREFIX + ".PASSWORD";
    private static final String USER_TOKEN_KEY = KEY_PREFIX + ".USER_TOKEN";
    private final List<Listener> listeners = new ArrayList<>();

    private final PreferenceDataStore preferences;

    RichPushUser(PreferenceDataStore preferenceDataStore) {
        this.preferences = preferenceDataStore;

        String password = preferences.getString(USER_PASSWORD_KEY, null);

        if (!UAStringUtil.isEmpty(password)) {
            String userToken = encode(password, preferences.getString(USER_ID_KEY, null));

            if (preferences.putSync(USER_TOKEN_KEY, userToken)) {
                preferences.remove(USER_PASSWORD_KEY);
            }
        }
    }

    /**
     * Subscribe a listener for user update events.
     *
     * @param listener An object implementing the {@link Listener} interface.
     */
    public void addListener(@NonNull Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Unsubscribe a listener for inbox and user update events.
     *
     * @param listener An object implementing the {@link Listener} interface.
     */
    public void removeListener(@NonNull Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Updates the user on the device with what's on the server.
     *
     * @param forcefully A boolean indicating if the rich push user needs to be updated.
     */
    public void update(boolean forcefully) {
        ResultReceiver resultReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                boolean success = resultCode == RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS;

                synchronized (listeners) {
                    for (Listener listener : new ArrayList<>(listeners)) {
                        listener.onUserUpdated(success);
                    }
                }
            }
        };

        Logger.debug("RichPushUser - Starting update service.");
        Context context = UAirship.getApplicationContext();
        Intent intent = new Intent(context, RichPushUpdateService.class)
                .setAction(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                .putExtra(RichPushUpdateService.EXTRA_FORCEFULLY, forcefully);

        context.startService(intent);
    }


    /**
     * Returns whether the user has been created.
     *
     * @return <code>true</code> if the user has an id, <code>false</code> otherwise.
     */
    public static boolean isCreated() {
        UAirship airship = UAirship.shared();
        String userId = airship.getInbox().getUser().getId();
        String userToken = airship.getInbox().getUser().getPassword();
        return (!UAStringUtil.isEmpty(userId) && !UAStringUtil.isEmpty(userToken));
    }

    /**
     * Updates the user
     *
     * @param userId The user ID from the response
     * @param userToken The user token from the response
     */
    void setUser(@NonNull String userId, @NonNull String userToken) {
        Logger.debug("RichPushUser - Setting Rich Push user: " + userId);
        preferences.put(USER_ID_KEY, userId);
        preferences.put(USER_TOKEN_KEY, encode(userToken, userId));
    }

    /**
     * Get the user's ID.
     *
     * @return A user ID String or null if it doesn't exist.
     */
    public String getId() {
        if (preferences.getString(USER_TOKEN_KEY, null) != null) {
            return preferences.getString(USER_ID_KEY, null);
        }
        return null;
    }

    /**
     * Get the user's token used for basic auth.
     *
     * @return A user token String.
     */
    public String getPassword() {
        if (preferences.getString(USER_ID_KEY, null) != null) {
            return decode(preferences.getString(USER_TOKEN_KEY, null), getId());
        }
        return null;
    }

    /**
     * Encode the string with the key.
     *
     * @param input The string to encode.
     * @param key The key used to encode the string.
     * @return The encoded string.
     */
    private static String encode(String input, String key) {
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
    private static String decode(String encodedString, String key) {

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
            Logger.error("RichPushUser - Unable to decode string. " + e.getMessage());
        } catch (NumberFormatException e) {
            Logger.error("RichPushUser - String contains invalid hex numbers. " + e.getMessage());
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
    private static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];

        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i % b.length]);
        }

        return out;
    }
}
