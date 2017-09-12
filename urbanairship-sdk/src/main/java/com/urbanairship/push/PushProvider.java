/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.support.annotation.NonNull;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;

/**
 * Defines a push provider.
 *
 * @hide
 */
public interface PushProvider {

    /**
     * Registration exceptions
     */
    class RegistrationException extends Exception {

        private final boolean isRecoverable;

        /**
         * Creates a new registration exception.
         *
         * @param message The exception message.
         * @param isRecoverable If the exception is recoverable (should retry registration).
         * @param cause The cause of the exception.
         */
        public RegistrationException(String message, boolean isRecoverable, Throwable cause) {
            super(message, cause);
            this.isRecoverable = isRecoverable;
        }

        /**
         * Creates a new registration exception.
         *
         * @param message The exception message.
         * @param isRecoverable If the exception is recoverable (should retry registration).
         */
        public RegistrationException(String message, boolean isRecoverable) {
            super(message);
            this.isRecoverable = isRecoverable;
        }

        /**
         * If the exception is recoverable or not.
         *
         * @return {@code true} if the exception is recoverable, otherwise {@code false}.
         */
        public boolean isRecoverable() {
            return isRecoverable;
        }
    }

    /**
     * Returns the platform type. Value must be either {@link UAirship#AMAZON_PLATFORM} or {@link UAirship#ANDROID_PLATFORM}.
     *
     * @return The platform type.
     */
    @UAirship.Platform
    int getPlatform();

    /**
     * Gets the push registration token.
     *
     * @param context The application context.
     * @return The registration ID.
     * @throws RegistrationException If the registration fails.
     */
    String getRegistrationToken(@NonNull Context context) throws RegistrationException;

    /**
     * If the underlying push provider is currently available.
     *
     * @param context The application context.
     * @return {@code true} if the push provider is currently available, otherwise {@code false}.
     */
    boolean isAvailable(@NonNull Context context);

    /**
     * If the underlying push provider is supported on the device.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @return {@code true} if the push provider is supported on the device, otherwise {@code false}.
     */
    boolean isSupported(@NonNull Context context, @NonNull AirshipConfigOptions configOptions);

    /**
     * Checks if the push message should be handled by the Urban Airship SDK.
     *
     * @param context The application context.
     * @param airship The airship instance.
     * @param message The push message.
     * @return {@code true} to allow the UA SDK to process the message, or {@code false} to ignore it.
     */
    boolean isUrbanAirshipMessage(@NonNull Context context, @NonNull UAirship airship, @NonNull PushMessage message);
}
