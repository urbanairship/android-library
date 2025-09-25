/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Context
import com.urbanairship.Airship
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Defines a push provider.
 */
public interface PushProvider {

    public enum class DeliveryType(internal val value: String): JsonSerializable {
        ADM("adm"), FCM("fcm"), HMS("hms");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(value)

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): DeliveryType {
                val content = value.requireString()
                return DeliveryType.entries.firstOrNull { it.value == content }
                    ?: throw JsonException("Invalid delivery type $content")
            }
        }

    }

    public class PushProviderUnavailableException : RegistrationException {
        public constructor(message: String, cause: Throwable?) : super(message, true, cause)

        public constructor(message: String) : super(message, true)
    }

    /**
     * Registration exceptions
     */
    public open class RegistrationException : Exception {

        /**
         * If the exception is recoverable or not.
         */
        public val isRecoverable: Boolean

        /**
         * Creates a new registration exception.
         *
         * @param message The exception message.
         * @param isRecoverable If the exception is recoverable (should retry registration).
         * @param cause The cause of the exception.
         */
        public constructor(
            message: String,
            isRecoverable: Boolean,
            cause: Throwable?
        ) : super(message, cause) {
            this.isRecoverable = isRecoverable
        }

        /**
         * Creates a new registration exception.
         *
         * @param message The exception message.
         * @param isRecoverable If the exception is recoverable (should retry registration).
         */
        public constructor(message: String, isRecoverable: Boolean) : super(message) {
            this.isRecoverable = isRecoverable
        }
    }

    public val platform: Airship.Platform

    public val deliveryType: DeliveryType

    /**
     * Gets the push registration token.
     *
     * @param context The application context.
     * @return The registration ID.
     * @throws RegistrationException If the registration fails.
     */
    @Throws(RegistrationException::class)
    public fun getRegistrationToken(context: Context): String?

    /**
     * If the underlying push provider is currently available.
     *
     * @param context The application context.
     * @return `true` if the push provider is currently available, otherwise `false`.
     */
    public fun isAvailable(context: Context): Boolean

    /**
     * If the underlying push provider is supported on the device.
     *
     * @param context The application context.
     * @return `true` if the push provider is supported on the device, otherwise `false`.
     */
    public fun isSupported(context: Context): Boolean
}
