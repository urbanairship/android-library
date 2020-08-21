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
     * @param identifier The identifier. Either the channel or named user Id.
     * @param attributes The attributes.
     */
    void onAttributeMutationsUploaded(@NonNull String identifier, @NonNull List<AttributeMutation> attributes);
}
