/* Copyright Airship and Contributors */

package com.urbanairship.images;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * Image loader.
 */
public interface ImageLoader {

    /**
     * Image loaded callback.
     */
    interface ImageLoadedCallback {

        /**
         * Called when the image has been loaded successfully.
         */
        void onImageLoaded();
    }

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
