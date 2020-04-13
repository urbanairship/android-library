/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;

import com.urbanairship.UAirship;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

/**
 * Defines a push provider.
 *
 * @hide
 */
public interface PushProvider {

    @StringDef({ ADM_DELIVERY_TYPE, FCM_DELIVERY_TYPE, HMS_DELIVERY_TYPE })
    @Retention(RetentionPolicy.SOURCE)
    @interface DeliveryType {
    }

    @NonNull
    String ADM_DELIVERY_TYPE = "adm";

    @NonNull
    String FCM_DELIVERY_TYPE = "fcm";

    @NonNull
    String HMS_DELIVERY_TYPE = "hms";

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
        public RegistrationException(@NonNull String message, boolean isRecoverable, @Nullable Throwable cause) {
            super(message, cause);
            this.isRecoverable = isRecoverable;
        }

        /**
         * Creates a new registration exception.
         *
         * @param message The exception message.
         * @param isRecoverable If the exception is recoverable (should retry registration).
         */
        public RegistrationException(@NonNull String message, boolean isRecoverable) {
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
     * Returns the delivery type.
     *
     * @return The delivery type.
     */
    @DeliveryType
    @NonNull
    String getDeliveryType();

    /**
     * Gets the push registration token.
     *
     * @param context The application context.
     * @return The registration ID.
     * @throws RegistrationException If the registration fails.
     */
    @Nullable
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
     * @return {@code true} if the push provider is supported on the device, otherwise {@code false}.
     */
    boolean isSupported(@NonNull Context context);
}
