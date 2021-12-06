/* Copyright Airship and Contributors */

package com.urbanairship.iam.layout;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Airship layout adapter factory.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipLayoutAdapterFactory implements InAppMessageAdapter.Factory {
    @NonNull
    @Override
    public InAppMessageAdapter createAdapter(@NonNull InAppMessage message) {
        return AirshipLayoutDisplayAdapter.newAdapter(message);
    }
}
