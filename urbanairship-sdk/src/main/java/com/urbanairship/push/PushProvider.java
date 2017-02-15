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
     * Checks if the registration Id is up to date. If true, the registration process will be skipped.
     *
     * @param context The application context.
     * @param registrationId The current registration Id.
     * @return {@code true} if the registration Id is up to date, otherwise {@code false}.
     */
    boolean shouldUpdateRegistration(@NonNull Context context, @NonNull String registrationId);

    /**
     * Starts registration process. After registration is finished, call {@link PushProviderBridge#registrationFinished(Context, Class, String)}.
     *
     * @param context The application context.
     * @throws IOException If the registration fails from an IOException. IOExceptions will trigger a retry with backoff.
     * @throws SecurityException If the registration fails from a SecurityException. SecurityException will trigger a retry with backoff.
     */
    void startRegistration(@NonNull Context context) throws IOException, SecurityException;

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
