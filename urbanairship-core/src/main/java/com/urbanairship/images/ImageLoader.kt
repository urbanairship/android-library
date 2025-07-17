/* Copyright Airship and Contributors */
package com.urbanairship.images

import android.content.Context
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo

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
    public fun interface ImageLoadedCallback {

        /**
         * Called when the image has finished loading, with a `boolean` indicating
         * whether the image was successfully loaded into the target image view.
         */
        public fun onImageLoaded(success: Boolean)
    }

    /**
     * Loads an image into an image view.
     *
     * @param context The context.
     * @param imageView The image view.
     * @param imageRequestOptions The request options.
     */
    @MainThread
    public fun load(
        context: Context,
        imageView: ImageView,
        imageRequestOptions: ImageRequestOptions
    )
}
