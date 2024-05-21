/* Copyright Airship and Contributors */

package com.urbanairship.images;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Image loader.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ImageLoader {

    /**
     * Image loaded callback.
     */
    interface ImageLoadedCallback {
        /**
         * Called when the image has finished loading, with a <code>boolean</code> indicating
         * whether the image was successfully loaded into the target image view.
         */
        void onImageLoaded(boolean success);
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
