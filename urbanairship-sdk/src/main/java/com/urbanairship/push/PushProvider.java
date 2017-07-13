/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.UAirship;

import java.io.IOException;

/**
 * Defines a push provider.
 *
 * @hide
 */
public interface PushProvider {

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
     * @throws IOException If the registration fails from an IOException. IOExceptions will trigger a retry with backoff.
     * @throws SecurityException If the registration fails from a SecurityException. SecurityException will trigger a retry with backoff.
     */
    String getRegistrationToken(@NonNull Context context) throws IOException, SecurityException;

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

    /**
     * Called to process a raw push bundle to {@link PushMessage}. Return {@code null} to ignore
     * the push.
     *
     * @param context The application context.
     * @param pushBundle The raw push bundle.
     * @return {@link PushMessage} or {@code null} to ignore the push.
     */
    @Nullable
    PushMessage processMessage(@NonNull Context context, @NonNull Bundle pushBundle);
}
