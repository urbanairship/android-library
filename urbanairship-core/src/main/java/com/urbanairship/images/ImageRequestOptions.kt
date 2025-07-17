/* Copyright Airship and Contributors */
package com.urbanairship.images

import androidx.annotation.DrawableRes
import androidx.annotation.RestrictTo
import com.urbanairship.images.ImageLoader.ImageLoadedCallback

/**
 * Image request options.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ImageRequestOptions private constructor(builder: Builder) {

    /**
     * Gets the place holder.
     *
     * @return The place holder.
     */
    @get:DrawableRes
    public val placeHolder: Int = builder.placeHolder

    /**
     * Gets the image URL.
     *
     * @return The image URL.
     */
    public val url: String = builder.url

    /**
     * Gets the [ImageLoadedCallback], if one was set.
     */
    public val callback: ImageLoadedCallback? = builder.callback
    public val imageSizeResolver: ImageSizeResolver? = builder.imageSizeResolver

    /**
     * Image request option builder.
     */
    public class Builder(internal val url: String) {

        public var placeHolder: Int = 0
            private set
        public var callback: ImageLoadedCallback? = null
            private set
        public var imageSizeResolver: ImageSizeResolver? = null
            private set

        /**
         * Sets the place holder.
         *
         * @param placeHolder The place holder resource.
         * @return The builder.
         */
        public fun setPlaceHolder(@DrawableRes placeHolder: Int): Builder {
            return this.also { it.placeHolder = placeHolder }
        }

        /**
         * Sets an [ImageLoadedCallback] to notify the caller when the image has been loaded.
         */
        public fun setImageLoadedCallback(callback: ImageLoadedCallback?): Builder {
            return this.also { it.callback = callback }
        }

        public fun setImageSizeResolver(imageSizeResolver: ImageSizeResolver?): Builder {
            return this.also { it.imageSizeResolver = imageSizeResolver }
        }

        /**
         * Builds the image request options.
         *
         * @return The image request options.
         */
        public fun build(): ImageRequestOptions {
            return ImageRequestOptions(this)
        }
    }

    public companion object {

        /**
         * Creates a new builder.
         *
         * @param url The image URL.
         * @return The builder.
         */
        public fun newBuilder(url: String): Builder {
            return Builder(url)
        }
    }
}
