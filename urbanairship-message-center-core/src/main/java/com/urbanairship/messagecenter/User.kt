/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import androidx.annotation.RestrictTo
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The Airship rich push user. */
public class User internal constructor(
    private val preferences: PreferenceStore
) {

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

    private val userUpdatedFlow = MutableSharedFlow<Boolean>()
    public val userUpdated: Flow<Boolean> = userUpdatedFlow
        .asSharedFlow()
        .distinctUntilChanged()

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

    internal suspend fun onUserUpdated(success: Boolean) {
        userUpdatedFlow.emit(success)

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
     * @param userCredentials The user credentials
     * @param channelId The channel Id that will be registered
     */
    internal fun onCreated(userCredentials: UserCredentials, channelId: String) {
        registeredChannelId = channelId
        setUser(userCredentials)
    }

    /** Returns `true` if the user credentials are available, otherwise `false. */
    public val isUserCreated: Boolean
        get() = !id.isNullOrEmpty() && !password.isNullOrEmpty()

    /**
     * Updates the user
     *
     * @param userCredentials The user credentials
     */
    internal fun setUser(userCredentials: UserCredentials?) {
        UALog.d("Setting Rich Push user: %s", userCredentials)
        val username = userCredentials?.username
        val password = userCredentials?.password
        preferences.put(USER_ID_KEY, username)
        preferences.put(
            USER_TOKEN_KEY,
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                xorHexEncode(password, username)
            } else {
                null
            }
        )
    }

    internal val userCredentials: UserCredentials?
        get() {
            val id = id ?: return null
            val password = password ?: return null
            return UserCredentials(id, password)
        }

    /** The user's ID. */
    public val id: String?
        get() = if (preferences.get(USER_TOKEN_KEY) != null) {
            preferences.get(USER_ID_KEY)
        } else {
            null
        }

    /** The user's token used for basic auth. */
    public val password: String?
        get() {
            val id = id ?: return null
            val token = preferences.get(USER_TOKEN_KEY) ?: return null
            return xorHexDecode(token, id)
        }

    /** The registered Channel ID stored in the DataStore, or `null` if no Channel ID is stored. */
    internal var registeredChannelId: String
        get() = preferences.get(USER_REGISTERED_CHANNEL_ID_KEY) ?: ""
        set(channelId) = preferences.put(USER_REGISTERED_CHANNEL_ID_KEY, channelId)


    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {

        private const val KEY_PREFIX = "com.urbanairship.user"
        private val USER_ID_KEY = SyncPrefKey.string("$KEY_PREFIX.ID")
        private val USER_TOKEN_KEY = SyncPrefKey.string("$KEY_PREFIX.USER_TOKEN")
        private val USER_REGISTERED_CHANNEL_ID_KEY = SyncPrefKey.string("$KEY_PREFIX.REGISTERED_CHANNEL_ID")

        /**
         * A flag indicating whether the user has been created.
         * @hide
         */
        @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public val isCreated: Boolean
            get() = Airship.messageCenter.user.isUserCreated

        /** XOR [input] with [key] (cycling), return result as lowercase hex. */
        private fun xorHexEncode(input: String, key: String): String {
            val inputBytes = input.toByteArray()
            val keyBytes = key.toByteArray()
            val out = ByteArray(inputBytes.size)
            for (i in inputBytes.indices) {
                out[i] = (inputBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return buildString(out.size * 2) {
                for (b in out) append(String.format("%02x", b))
            }
        }

        /** Inverse of [xorHexEncode]. Returns `null` on malformed input. */
        private fun xorHexDecode(hex: String, key: String): String? {
            if (hex.length % 2 != 0) return null
            return try {
                val bytes = ByteArray(hex.length / 2)
                var i = 0
                while (i < hex.length) {
                    bytes[i / 2] = hex.substring(i, i + 2).toByte(16)
                    i += 2
                }
                val keyBytes = key.toByteArray()
                for (j in bytes.indices) {
                    bytes[j] = (bytes[j].toInt() xor keyBytes[j % keyBytes.size].toInt()).toByte()
                }
                String(bytes, Charsets.UTF_8)
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}
