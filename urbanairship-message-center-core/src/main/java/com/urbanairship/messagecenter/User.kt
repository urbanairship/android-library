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

/**
 * The Airship rich push user.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class User internal constructor(
    private val preferences: PreferenceStore,
    private val tokenCipher: UserTokenCipher = KeystoreUserTokenCipher()
) {

    /**
     * A listener interface for receiving events for user updates.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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

    /** @hide */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val userUpdated: Flow<Boolean> = userUpdatedFlow
        .asSharedFlow()
        .distinctUntilChanged()

    /**
     * Subscribe a listener for user update events.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    /**
     * Unsubscribe a listener for inbox and user update events.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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

    /**
     * Returns `true` if the user credentials are available, otherwise `false.
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
                encryptToken(password)
            } else {
                null
            }
        )
    }

    private fun encryptToken(token: String): String? =
        tokenCipher.encrypt(token)?.let { ENCRYPTED_TOKEN_PREFIX + it }

    internal val userCredentials: UserCredentials?
        get() {
            val id = id ?: return null
            val password = password ?: return null
            return UserCredentials(id, password)
        }

    /**
     * The user's ID.
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val id: String?
        get() = if (preferences.get(USER_TOKEN_KEY) != null) {
            preferences.get(USER_ID_KEY)
        } else {
            null
        }

    /**
     * The user's token used for basic auth.
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val password: String?
        get() {
            val id = id ?: return null
            val token = preferences.get(USER_TOKEN_KEY) ?: return null

            if (token.startsWith(ENCRYPTED_TOKEN_PREFIX)) {
                return tokenCipher.decrypt(token.removePrefix(ENCRYPTED_TOKEN_PREFIX))
            }

            val legacyToken = xorHexDecode(token, id) ?: return null
            encryptToken(legacyToken)?.let { preferences.put(USER_TOKEN_KEY, it) }
            return legacyToken
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

        /** Marks a token stored by [KeystoreUserTokenCipher]; anything else is the legacy XOR format. */
        private const val ENCRYPTED_TOKEN_PREFIX = "v1:"

        /**
         * Decodes the legacy XOR-obfuscated token format, read-only for migration.
         * Returns `null` on malformed input.
         */
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
