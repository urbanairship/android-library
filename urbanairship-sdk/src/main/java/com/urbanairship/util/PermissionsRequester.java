/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;


import android.content.Context;
import android.support.annotation.NonNull;

import java.util.List;

/**
 * Interface used to request permissions.
 */
public interface PermissionsRequester {

    /**
     * Request permissions.
     *
     * @param context The application context.
     * @param permissions The permissions to request.
     * @return The result from requesting permissions.
     */
    @NonNull
    int[] requestPermissions(@NonNull  Context context, @NonNull List<String> permissions);
}
