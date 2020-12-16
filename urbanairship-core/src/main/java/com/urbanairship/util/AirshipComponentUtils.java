package com.urbanairship.util;

import com.urbanairship.AirshipComponent;
import com.urbanairship.UAirship;

import java.util.concurrent.Callable;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Airship component utils.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipComponentUtils {

    /**
     * Creates a callable that returns an Airship Component.
     *
     * @param clazz The component's class.
     * @return A callable that returns the Airship Component when called.
     */
    @NonNull
    public static <T extends AirshipComponent> Callable<T> callableForComponent(@NonNull final Class<T> clazz) {
        return new Callable<T>() {
            @Override
            public T call() {
                return UAirship.shared().requireComponent(clazz);
            }
        };
    }

}
