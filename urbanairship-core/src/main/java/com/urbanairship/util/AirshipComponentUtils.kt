package com.urbanairship.util

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.Airship
import java.util.concurrent.Callable

/**
 * Airship component utils.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object AirshipComponentUtils {

    /**
     * Creates a callable that returns an Airship Component.
     *
     * @param clazz The component's class.
     * @return A callable that returns the Airship Component when called.
     */
    public fun <T : AirshipComponent> callableForComponent(clazz: Class<T>): Callable<T> {
        return Callable<T> { Airship.requireComponent(clazz) }
    }
}
