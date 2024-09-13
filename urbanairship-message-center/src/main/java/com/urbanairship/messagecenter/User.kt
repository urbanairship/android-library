/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import androidx.annotation.RestrictTo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.channel.AirshipChannel
import java.io.UnsupportedEncodingException
import java.util.concurrent.CopyOnWriteArrayList

/** The Airship rich push user. */
public class User internal constructor(
    private val preferences: PreferenceDataStore,
    private val channel: AirshipChannel
) {

    init {
        val password = preferences.getString(USER_PASSWORD_KEY, null)
        if (!password.isNullOrEmpty()) {
            val userToken = encode(password, preferences.getString(USER_ID_KEY, null))
            if (preferences.putSync(USER_TOKEN_KEY, userToken)) {
                preferences.remove(USER_PASSWORD_KEY)
            }
        }
    }

    /** A listener interface for receiving events for user updates. */
    public fun interface Listener {

        /**
         * Called when the user is updated.
         *
         * @param success `true` if the request was successful, otherwise `false`.
         */
        public fun onUserUpdated(success: Boolean)
    }

    private val listeners: MutableList<Listener> = CopyOnWriteArrayList()

    /**
     * Subscribe a listener for user update events.
     *
     * @param listener An object implementing the [Listener] interface.
     */
    public fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    /**
     * Unsubscribe a listener for inbox and user update events.
     *
     * @param listener An object implementing the [Listener] interface.
     */
    public fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    internal fun onUserUpdated(success: Boolean) {
        for (listener in listeners) {
            listener.onUserUpdated(success)
        }
    }

    /**
     * Verify that the user's registered channel ID is the correct one after an update.
     *
     * @param channelId The channelId
     */
    internal fun onUpdated(channelId: String) {
        if (channelId != registeredChannelId) {
            preferences.put(USER_REGISTERED_CHANNEL_ID_KEY, channelId)
        }
    }

    /**
     * Set private properties to the User when it's created.
     *
     * @param userId The user's Id
     * @param userToken The user's token
     * @param channelId The channel Id that will be registered
     */
    internal fun onCreated(userId: String, userToken: String, channelId: String) {
        registeredChannelId = channelId
        setUser(userId, userToken)
    }

    /** Returns `true` if the user credentials are available, otherwise `false. */
    public val isUserCreated: Boolean
        get() = !id.isNullOrEmpty() && !password.isNullOrEmpty()

    /**
     * Updates the user
     *
     * @param userId The user ID from the response
     * @param userToken The user token from the response
     */
    internal fun setUser(userId: String?, userToken: String?) {
        UALog.d("Setting Rich Push user: %s", userId)
        preferences.put(USER_ID_KEY, userId)
        preferences.put(USER_TOKEN_KEY, encode(userToken, userId))
    }

    /** The user's ID. */
    public val id: String?
        get() = if (preferences.getString(USER_TOKEN_KEY, null) != null) {
            preferences.getString(USER_ID_KEY, null)
        } else {
            null
        }

    /** The user's token used for basic auth. */
    public val password: String?
        get() = if (preferences.getString(USER_ID_KEY, null) != null) {
            decode(preferences.getString(USER_TOKEN_KEY, null), id)
        } else {
            null
        }

    /** The registered Channel ID stored in the DataStore, or `null` if no Channel ID is stored. */
    private var registeredChannelId: String
        get() = preferences.getString(USER_REGISTERED_CHANNEL_ID_KEY, "")
        set(channelId) = preferences.put(USER_REGISTERED_CHANNEL_ID_KEY, channelId)

    /** Returns `true` if the user should be updated. */
    internal fun shouldUpdate(): Boolean {
        return channel.id != null && registeredChannelId != channel.id
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {

        private const val KEY_PREFIX = "com.urbanairship.user"
        private const val USER_ID_KEY = "${KEY_PREFIX}.ID"
        private const val USER_PASSWORD_KEY = "${KEY_PREFIX}.PASSWORD"
        private const val USER_TOKEN_KEY = "${KEY_PREFIX}.USER_TOKEN"
        private const val USER_REGISTERED_CHANNEL_ID_KEY = "${KEY_PREFIX}.REGISTERED_CHANNEL_ID"

        /** A flag indicating whether the user has been created. */
        @JvmStatic
        public val isCreated: Boolean
            get() = MessageCenter.shared().user.isUserCreated

        /**
         * Encode the string with the key.
         *
         * @param input The string to encode.
         * @param key The key used to encode the string.
         * @return The encoded string.
         */
        private fun encode(input: String?, key: String?): String? {
            if (input.isNullOrEmpty() || key.isNullOrEmpty()) {
                return null
            }

            // xor the two strings together
            val bytes = xor(input.toByteArray(), key.toByteArray())

            // Format the raw byte array as a hex string
            val hexHash = StringBuilder()
            for (b in bytes) {
                hexHash.append(String.format("%02x", b))
            }
            return hexHash.toString()
        }

        /**
         * Decode the string with the key.
         *
         * @param encodedString The string to decode.
         * @param key The key used to decode the string.
         * @return The decoded string.
         */
        private fun decode(encodedString: String?, key: String?): String? {
            if (encodedString.isNullOrEmpty() || key.isNullOrEmpty()) {
                return null
            }

            val length = encodedString.length
            // Make sure we have an even number of chars
            if (length % 2 != 0) {
                return null
            }

            try {
                // Decode the encodedString to a byte array
                var decodedBytes = ByteArray(length / 2)
                var i = 0
                while (i < length) {
                    decodedBytes[i / 2] = encodedString.substring(i, i + 2).toByte(16)
                    i += 2
                }
                decodedBytes = xor(decodedBytes, key.toByteArray())
                return String(decodedBytes, charset("UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                UALog.e(e, "RichPushUser - Unable to decode string.")
            } catch (e: NumberFormatException) {
                UALog.e(e, "RichPushUser - String contains invalid hex numbers.")
            }
            return null
        }

        /**
         * Compare and return the xor value.
         *
         * @param a The byte value.
         * @param b The byte value.
         * @return The byte result value.
         */
        private fun xor(a: ByteArray, b: ByteArray): ByteArray {
            val out = ByteArray(a.size)
            for (i in a.indices) {
                out[i] = (a[i].toInt() xor b[i % b.size].toInt()).toByte()
            }
            return out
        }
    }
}
