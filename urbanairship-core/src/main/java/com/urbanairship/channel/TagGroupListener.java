package com.urbanairship.channel;

import java.util.List;

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
     * @param tagGroupsMutation The tag groups mutation.
     */
    void onTagGroupsMutationUploaded(@NonNull List<TagGroupsMutation> tagGroupsMutation);
}
