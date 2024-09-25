package com.urbanairship.images

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Size
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target

/**
 * Airship Glide image loader.
 *
 * @hide
 */
internal object AirshipGlideImageLoader : ImageLoader {
    override fun load(
        context: Context, imageView: ImageView, imageRequestOptions: ImageRequestOptions
    ) {
        val listener = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                imageRequestOptions.callback?.onImageLoaded(false)

                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                imageRequestOptions.callback?.onImageLoaded(true)

                // Make Glide respect the GIF's intrinsic loop count, instead of looping forever.
                // Glide handles starting the animation, so we don't need to do anything else here.
                if (resource is GifDrawable) {
                    resource.setLoopCount(GifDrawable.LOOP_INTRINSIC)
                }

                return false
            }
        }

        Glide.with(imageView)
            .load(imageRequestOptions.url)
            .addListener(listener)
            .apply {
                if (imageRequestOptions.placeHolder != 0) {
                    placeholder(imageRequestOptions.placeHolder)
                }
            }
            .transition(withCrossFade(100))
            .into(
                AirshipImageViewTarget(
                    imageView,
                    imageRequestOptions.zeroWidthFallback,
                    imageRequestOptions.zeroHeightFallback
                )
            )
    }

    private class AirshipImageViewTarget(
        view: ImageView,
        private val zeroWidthFallback: Int?,
        private val zeroHeightFallback: Int?,
        private val subtractPadding: Boolean = true,
    ) : DrawableImageViewTarget(view) {

        override fun getSize(cb: SizeReadyCallback) {
            // Fast path: the view is already measured or has fallback width/height
            val size = getSize(false)
            if (size.width > 0 && size.height > 0) {
                cb.onSizeReady(size.width, size.height)
            } else {
                // Slow path: wait for the view to be measured...
                val viewTreeObserver = view.viewTreeObserver
                val preDrawListener = object : OnPreDrawListener {
                    private var isResumed = false

                    override fun onPreDraw(): Boolean {
                        getSize(true).let { size ->
                            viewTreeObserver.removePreDrawListenerSafe(this)

                            if (!isResumed) {
                                isResumed = true
                                cb.onSizeReady(size.width, size.height)
                            }
                        }
                        return true
                    }
                }

                viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
        }

        fun getSize(allowTargetSize: Boolean): Size = Size(
            getWidth().let { value ->  if (value <= 0 && allowTargetSize) zeroWidthFallback?: Target.SIZE_ORIGINAL else value  },
            getHeight().let { value ->  if (value <= 0 && allowTargetSize) zeroHeightFallback?: Target.SIZE_ORIGINAL else value  },
        )

        fun getWidth() = getDimension(
            paramSize = view.layoutParams?.width ?: ViewGroup.LayoutParams.MATCH_PARENT,
            viewSize = view.width,
            fallbackSize = zeroWidthFallback,
            paddingSize = if (subtractPadding) view.paddingLeft + view.paddingRight else 0
        )

        fun getHeight() = getDimension(
            paramSize = view.layoutParams?.height ?: ViewGroup.LayoutParams.MATCH_PARENT,
            viewSize = view.height,
            fallbackSize = zeroHeightFallback,
            paddingSize = if (subtractPadding) view.paddingTop + view.paddingBottom else 0
        )

        fun getDimension(
            paramSize: Int,
            viewSize: Int,
            fallbackSize: Int?,
            paddingSize: Int
        ): Int {
            // Assume the dimension will match the value in the view's layout params.
            val insetParamSize = paramSize - paddingSize
            if (insetParamSize > 0) {
                return insetParamSize
            }

            // Fallback to the view's current dimension.
            val insetViewSize = viewSize - paddingSize
            if (insetViewSize > 0) {
                return insetViewSize
            }

            if (paramSize == ViewGroup.LayoutParams.MATCH_PARENT) {
                return fallbackSize ?: 0
            }

            // Unable to resolve the dimension's value.
            return 0
        }

        private fun ViewTreeObserver.removePreDrawListenerSafe(victim: OnPreDrawListener) {
            if (isAlive) {
                removeOnPreDrawListener(victim)
            } else {
                view.viewTreeObserver.removeOnPreDrawListener(victim)
            }
        }
    }
}
