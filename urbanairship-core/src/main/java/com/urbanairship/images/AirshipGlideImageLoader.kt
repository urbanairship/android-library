package com.urbanairship.images

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Size
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.ImageView
import androidx.core.view.ancestors
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition


public interface ImageSizeResolver {
    public fun resolveHeight(context: Context, measuredWidth: Int?): Int?
    public fun resolveWidth(context: Context, measuredHeight: Int?): Int?
}

/**
 * Airship Glide image loader.
 *
 * @hide
 */
internal object AirshipGlideImageLoader : ImageLoader {
    override fun load(
        context: Context,
        imageView: ImageView,
        imageRequestOptions: ImageRequestOptions
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
                    imageRequestOptions
                )
            )
    }

    private class AirshipImageViewTarget(
        view: ImageView,
        private val imageRequestOptions: ImageRequestOptions,
        private val subtractPadding: Boolean = true,
    ) : DrawableImageViewTarget(view) {

        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
            super.onResourceReady(resource, transition)

            view.ancestors.forEach { it.requestLayout() }
        }

        override fun getSize(cb: SizeReadyCallback) {
            // Fast path: the view is already measured
            val size = getMeasuredSize(view.context)
            if (size != null) {
                cb.onSizeReady(size.width, size.height)
            } else {
                val viewTreeObserver = view.viewTreeObserver
                val preDrawListener = object : OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        viewTreeObserver.removePreDrawListenerSafe(this)

                        resolveSize(view.context).let { size ->
                            cb.onSizeReady(size.width, size.height)
                        }

                        return true
                    }
                }
                viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
        }

        fun resolveSize(context: Context): Size {
            val measuredHeight = getMeasuredHeight()
            val measuredWidth = getMeasuredWith()

            val displayWidth = context.resources.displayMetrics.widthPixels
            val displayHeight = context.resources.displayMetrics.heightPixels

            return Size(
                measuredWidth ?: imageRequestOptions.imageSizeResolver?.resolveWidth(context, measuredHeight) ?: displayWidth,
                measuredHeight ?: imageRequestOptions.imageSizeResolver?.resolveHeight(context, measuredWidth) ?: displayHeight
            )
        }

        fun getMeasuredSize(context: Context): Size? {
            var measuredHeight = getMeasuredHeight()
            var measuredWidth = getMeasuredWith()

            if (measuredHeight == null && measuredWidth != null) {
                measuredHeight = imageRequestOptions.imageSizeResolver?.resolveHeight(context, measuredWidth)
            }

            if (measuredWidth == null && measuredHeight != null) {
                measuredWidth = imageRequestOptions.imageSizeResolver?.resolveWidth(context, measuredHeight)
            }

            return if (measuredHeight != null && measuredWidth != null) {
                Size(measuredWidth, measuredHeight)
            } else {
                null
            }
        }

        fun getMeasuredWith(): Int? {
            return getMeasuredDimension(
                paramSize = view.layoutParams?.width ?: ViewGroup.LayoutParams.MATCH_PARENT,
                viewSize = view.width,
                paddingSize = if (subtractPadding) view.paddingLeft + view.paddingRight else 0
            )
        }


        fun getMeasuredHeight(): Int? {
            return getMeasuredDimension(
                paramSize = view.layoutParams?.height ?: ViewGroup.LayoutParams.MATCH_PARENT,
                viewSize = view.height,
                paddingSize = if (subtractPadding) view.paddingTop + view.paddingBottom else 0
            )
        }

        fun getMeasuredDimension(
            paramSize: Int,
            viewSize: Int,
            paddingSize: Int
        ): Int? {
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

            return null
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
