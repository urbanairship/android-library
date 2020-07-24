package com.urbanairship.channel;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Tag group upload listener.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface TagGroupListener {

    /**
     * Called when a tag group is uploaded.
     *
     * @param identifier The identifier. Either the channel or named user Id.
     * @param tagGroupsMutation The tag groups mutation.
     */
    void onTagGroupsMutationUploaded(@NonNull String identifier, @NonNull TagGroupsMutation tagGroupsMutation);
}
