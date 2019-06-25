/* Copyright Airship and Contributors */

package com.urbanairship.images;

import android.content.Context;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
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
