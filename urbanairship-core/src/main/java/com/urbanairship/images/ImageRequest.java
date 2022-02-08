package com.urbanairship.images;

import android.content.Context;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.CancelableOperation;
import com.urbanairship.Logger;
import com.urbanairship.util.ImageUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.concurrent.Executor;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

/**
 * Request to load a bitmap into an ImageView.
 */
abstract class ImageRequest {

    private final Executor EXECUTOR = AirshipExecutors.threadPoolExecutor();

    /**
     * Duration of the fade in animation when loading a bitmap into the image view in milliseconds.
     */
    private static final int FADE_IN_TIME_MS = 200;

    private final ImageRequestOptions imageRequestOptions;
    private final ImageCache imageCache;
    private final WeakReference<ImageView> imageViewReference;
    private final Context context;

    private final CancelableOperation pendingRequest = new CancelableOperation();

    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private int width;
    private int height;

    /**
     * Creates a request.
     *
     * @param context The application context.
     * @param imageCache The image cache.
     * @param imageView The image view.
     * @param imageRequestOptions The request options.
     */
    ImageRequest(@NonNull Context context,
                 @NonNull ImageCache imageCache,
                 @NonNull ImageView imageView,
                 @NonNull ImageRequestOptions imageRequestOptions) {

        this.context = context;
        this.imageCache = imageCache;
        this.imageRequestOptions = imageRequestOptions;
        this.imageViewReference = new WeakReference<>(imageView);
    }

    /**
     * Cancels a request.
     */
    @MainThread
    void cancel() {
        ImageView imageView = imageViewReference.get();
        if (imageView != null && preDrawListener != null) {
            imageView.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            imageViewReference.clear();
        }
        pendingRequest.cancel();
    }

    /**
     * Executes the request.
     */
    @MainThread
    void execute() {
        if (pendingRequest.isCancelled()) {
            return;
        }

        final ImageView imageView = imageViewReference.get();
        if (imageView == null) {
            onFinish(null);
            return;
        }

        width = imageView.getWidth();
        height = imageView.getHeight();

        // If the width and height are not available the image view has yet to be drawn on the screen.
        // Add a predraw listener to be notified when the height and width are available
        if (width == 0 && height == 0) {
            this.preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ImageView imageView = imageViewReference.get();

                    if (imageView != null) {
                        imageView.getViewTreeObserver().removeOnPreDrawListener(this);

                        if (imageView.getViewTreeObserver().isAlive()) {
                            if (imageView.getHeight() == 0 && imageView.getWidth() == 0) {
                                onFinish(imageView);
                            } else {
                                execute();
                            }
                        }
                    }

                    return true;
                }
            };

            imageView.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            return;
        }

        Drawable cachedEntry = imageCache.getDrawable(getCacheKey());

        if (cachedEntry != null) {
            imageView.setImageDrawable(cachedEntry);
            onFinish(imageView);
        } else {
            if (imageRequestOptions.getPlaceHolder() != 0) {
                imageView.setImageResource(imageRequestOptions.getPlaceHolder());
            } else {
                imageView.setImageDrawable(null);
            }

            EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    if (pendingRequest.isCancelled()) {
                        return;
                    }

                    try {
                        final Drawable drawable = fetchDrawableOnBackground();

                        if (drawable != null) {
                            pendingRequest.addOnRun(new Runnable() {
                                @Override
                                public void run() {
                                    if (pendingRequest.isCancelled()) {
                                        return;
                                    }

                                    applyDrawable(drawable);
                                    onFinish(imageView);
                                }
                            });

                            pendingRequest.run();
                        }

                    } catch (IOException e) {
                        Logger.debug(e, "Unable to fetch bitmap");
                    }
                }
            });
        }
    }

    /**
     * Returns the memory cache key for the request.
     *
     * @return The memory cache key.
     */
    @NonNull
    private String getCacheKey() {
        return imageRequestOptions.getUrl() == null ? "" : imageRequestOptions.getUrl() + ",size(" + width + "x" + height + ")";
    }

    /**
     * Called when the request is finished.
     *
     * @param imageView The image view.
     */
    abstract void onFinish(@Nullable ImageView imageView);

    @Nullable
    @WorkerThread
    private Drawable fetchDrawableOnBackground() throws IOException {
        imageCache.installHttpCache();

        if (imageViewReference.get() == null) {
            return null;
        }

        if (imageRequestOptions.getUrl() == null) {
            return null;
        }

        int fallbackWidth = imageRequestOptions.getZeroWidthFallback();
        int fallbackHeight = imageRequestOptions.getZeroHeightFallback();
        ImageUtils.DrawableResult result = ImageUtils.fetchScaledDrawable(context, new URL(imageRequestOptions.getUrl()), width, height, fallbackWidth, fallbackHeight);

        if (result == null) {
            return null;
        }
        imageCache.cacheDrawable(getCacheKey(), result.drawable, result.bytes);
        return result.drawable;

    }

    @MainThread
    private void applyDrawable(Drawable drawable) {
        final ImageView imageView = imageViewReference.get();
        if (drawable != null && imageView != null) {
            // Transition drawable with a transparent drawable and the final drawable
            TransitionDrawable td = new TransitionDrawable(new Drawable[] {
                    new ColorDrawable(ContextCompat.getColor(context, android.R.color.transparent)),
                    drawable
            });

            imageView.setImageDrawable(td);
            td.startTransition(FADE_IN_TIME_MS);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable instanceof AnimatedImageDrawable) {
                ((AnimatedImageDrawable) drawable).start();
            }
        }
    }
}
