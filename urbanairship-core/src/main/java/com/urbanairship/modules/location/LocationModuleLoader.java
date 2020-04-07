/* Copyright Airship and Contributors */

package com.urbanairship.modules.location;

import com.urbanairship.modules.ModuleLoader;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Location module loader.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface LocationModuleLoader extends ModuleLoader {

    @NonNull
    AirshipLocationClient getLocationClient();

}
