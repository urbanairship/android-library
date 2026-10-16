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
     * Whether the loader should set the resolved drawable immediately rather than animate it in.
     *
     * A crossfade only ever completes on a target view that's attached to a window -- set this for
     * a view that is never attached (e.g. one only ever drawn into a `Canvas` directly), or the
     * drawable silently never gets set at all.
     */
    public val disableTransition: Boolean = builder.disableTransition

    /**
     * Image request option builder.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public class Builder(internal val url: String) {

        public var placeHolder: Int = 0
            private set
        public var callback: ImageLoadedCallback? = null
            private set
        public var imageSizeResolver: ImageSizeResolver? = null
            private set
        public var disableTransition: Boolean = false
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

        /** See [ImageRequestOptions.disableTransition]. */
        public fun disableTransition(disableTransition: Boolean = true): Builder {
            return this.also { it.disableTransition = disableTransition }
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

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
