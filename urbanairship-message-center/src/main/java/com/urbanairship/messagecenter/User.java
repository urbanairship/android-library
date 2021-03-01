/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.util.UAStringUtil;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Airship rich push user.
 */
public class User {

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
    private static final String USER_REGISTERED_CHANNEL_ID_KEY = KEY_PREFIX + ".REGISTERED_CHANNEL_ID";
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final PreferenceDataStore preferences;
    private final AirshipChannel channel;

    User(@NonNull PreferenceDataStore preferenceDataStore, @NonNull AirshipChannel channel) {
        this.preferences = preferenceDataStore;
        this.channel = channel;
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
        listeners.add(listener);
    }

    /**
     * Unsubscribe a listener for inbox and user update events.
     *
     * @param listener An object implementing the {@link Listener} interface.
     */
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    void onUserUpdated(boolean success) {
        for (Listener listener : listeners) {
            listener.onUserUpdated(success);
        }
    }

    /**
     * Verify that the user's registered channel ID is the correct one after an update.
     *
     * @param channelId The channelId
     */
    void onUpdated(@NonNull String channelId) {
        if (!channelId.equals(this.getRegisteredChannelId())) {
            preferences.put(USER_REGISTERED_CHANNEL_ID_KEY, channelId);
        }
    }

    /**
     * Set private properties to the User when it's created.
     *
     * @param userId The user's Id
     * @param userToken The user's token
     * @param channelId The channel Id that will be registered
     */
    void onCreated(@NonNull String userId, @NonNull String userToken, @NonNull String channelId) {
        this.setRegisteredChannelId(channelId);
        this.setUser(userId, userToken);
    }

    /**
     * Returns whether the user has been created.
     *
     * @return <code>true</code> if the user has an id, <code>false</code> otherwise.
     */
    public static boolean isCreated() {
        return MessageCenter.shared().getUser().isUserCreated();
    }

    /**
     * Checks if the user credentials are available.
     *
     * @return {@code true} if the credentials are available, otherwise {@code false}.
     */
    public boolean isUserCreated() {
        return (!UAStringUtil.isEmpty(getId()) && !UAStringUtil.isEmpty(getPassword()));
    }

    /**
     * Updates the user
     *
     * @param userId The user ID from the response
     * @param userToken The user token from the response
     */
    void setUser(@NonNull String userId, @NonNull String userToken) {
        Logger.debug("Setting Rich Push user: %s", userId);
        preferences.put(USER_ID_KEY, userId);
        preferences.put(USER_TOKEN_KEY, encode(userToken, userId));
    }

    /**
     * Get the user's ID.
     *
     * @return A user ID String or null if it doesn't exist.
     */
    @Nullable
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
    @Nullable
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
    @Nullable
    private static String encode(@Nullable String input, @Nullable String key) {
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
    @Nullable
    private static String decode(@Nullable String encodedString, @Nullable String key) {
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
            Logger.error(e, "RichPushUser - Unable to decode string.");
        } catch (NumberFormatException e) {
            Logger.error(e, "RichPushUser - String contains invalid hex numbers.");
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
    private static byte[] xor(@NonNull byte[] a, @NonNull byte[] b) {
        byte[] out = new byte[a.length];

        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i % b.length]);
        }

        return out;
    }

    /**
     * Retrieve and return the registered Channel ID stored in the DataStore.
     * If none is stored, return an empty string.
     *
     * @return The registered Channel ID String
     */
    @NonNull
    private String getRegisteredChannelId() {
        return preferences.getString(USER_REGISTERED_CHANNEL_ID_KEY, "");
    }

    /**
     * Save in the DataStore the Channel ID used for registration.
     *
     * @param channelId The ChannelId String
     */
    private void setRegisteredChannelId(@NonNull String channelId) {
        preferences.put(USER_REGISTERED_CHANNEL_ID_KEY, channelId);
    }

    boolean shouldUpdate() {
        return channel.getId() != null && (!this.getRegisteredChannelId().equals(channel.getId()));
    }

}
