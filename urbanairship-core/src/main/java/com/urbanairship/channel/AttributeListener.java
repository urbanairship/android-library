package com.urbanairship.channel;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Attribute upload listener.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AttributeListener {

    /**
     * Called when attributes are uploaded.
     *
     * @param attributes The attributes.
     */
    void onAttributeMutationsUploaded(@NonNull List<AttributeMutation> attributes);
}
