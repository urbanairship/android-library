/* Copyright Airship and Contributors */

package com.urbanairship.images;

import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.widget.ImageView;

/**
 * Image loader.
 */
public interface ImageLoader {

    /**
     * Loads an image into an image view.
     *
     * @param context The context.
     * @param imageView The image view.
     * @param imageRequestOptions The request options.
     */
    @MainThread
    void load(@NonNull Context context, @NonNull ImageView imageView, @NonNull ImageRequestOptions imageRequestOptions);

}
