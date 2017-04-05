/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.http.HttpResponseCache;
import android.os.AsyncTask;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.LruCache;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.urbanairship.Logger;
import com.urbanairship.util.BitmapUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Asynchronous bitmap loader for image views.
 */
class ImageLoader {

    private static final String CACHE_DIR = "urbanairship-cache";

    /**
     * Max amount of memory cache.
     */
    private static final int MAX_MEM_CACHE_SIZE = 1024 * 1024 * 10; // 10MB

    /**
     * Disk cache size.
     */
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 50; // 50MB

    /**
     * How long the fade in animation when loading a bitmap into the image view in milliseconds.
     */
    private static final int FADE_IN_TIME_MS = 200;

    private final Executor executor;
    private final Context context;
    private final Map<ImageView, Request> requestMap;
    private final LruCache<String, BitmapDrawable> memoryCache;

    /**
     * Creates an ImageLoader.
     *
     * @param context The application context.
     */
    ImageLoader(Context context) {
        this.context = context.getApplicationContext();
        this.requestMap = new WeakHashMap<>();
        this.executor = Executors.newFixedThreadPool(2);

        // Memory Cache
        int memCacheSize = (int) Math.min(MAX_MEM_CACHE_SIZE, Runtime.getRuntime().maxMemory() / 8);
        this.memoryCache = new LruCache<String, BitmapDrawable>(memCacheSize) {
            @Override
            protected int sizeOf(String key, BitmapDrawable bitmapDrawable) {
                return bitmapDrawable.getBitmap().getByteCount();
            }
        };
    }

    /**
     * Cancels a request.
     *
     * @param imageView The imageView.
     */
    void cancelRequest(ImageView imageView) {
        if (imageView == null) {
            return;
        }

        Request request = requestMap.remove(imageView);
        if (request != null) {
            request.cancel();
        }
    }

    /**
     * Loads an image into an image view.
     * @param imageUrl The url to load.
     * @param placeHolder The optional placeholder.
     * @param imageView The image view.
     */
    void load(String imageUrl, @DrawableRes int placeHolder, @NonNull ImageView imageView) {
        cancelRequest(imageView);

        Request request = new Request(imageUrl, placeHolder, imageView) {
            @Override
            void onFinish() {
                ImageView imageView = getImageView();
                if (imageView != null) {
                    requestMap.remove(imageView);
                }
            }
        };
        requestMap.put(imageView, request);
        request.execute();
    }

    /**
     * Request to load a bitmap into an ImageView.
     */
    private abstract class Request implements ViewTreeObserver.OnPreDrawListener {
        private final String imageUrl;
        private final int placeHolder;
        private BitmapAsyncTask task;
        private int width;
        private int height;
        private final WeakReference<ImageView> imageViewReference;

        /**
         * Creates a request.
         *
         * @param imageUrl The image url.
         * @param placeHolder The optional placeholder.
         * @param imageView The ImageView.
         */
        Request(String imageUrl, int placeHolder, ImageView imageView) {
            this.placeHolder = placeHolder;
            this.imageUrl = imageUrl;
            this.imageViewReference = new WeakReference<>(imageView);
            this.width = imageView.getWidth();
            this.height = imageView.getHeight();
        }

        /**
         * Cancels a request.
         */
        void cancel() {
            ImageView imageView = getImageView();
            if (imageView != null) {
                imageView.getViewTreeObserver().removeOnPreDrawListener(this);
                imageViewReference.clear();
            }

            if (task != null) {
                task.cancel(true);
                task = null;
            }
        }

        /**
         * Gets the Request's ImageView.
         *
         * @return The image view or {@code null} if the image view is garbage collected.
         */
        @Nullable
        ImageView getImageView() {
            return imageViewReference.get();
        }

        /**
         * Called when the request finishes loading the bitmap into the ImageView.
         */
        abstract void onFinish();

        /**
         * Executes the request.
         */
        void execute() {
            ImageView imageView = getImageView();
            if (imageView == null) {
                onFinish();
                return;
            }

            if (width == 0 && height == 0) {
                if (imageView.getWidth() == 0 && imageView.getHeight() == 0) {
                    imageView.getViewTreeObserver().addOnPreDrawListener(this);
                    return;
                } else {
                    width = imageView.getWidth();
                    height = imageView.getHeight();
                }
            }

            BitmapDrawable cachedBitmapDrawable = memoryCache.get(getCacheKey());
            if (cachedBitmapDrawable != null) {
                imageView.setImageDrawable(cachedBitmapDrawable);
                onFinish();
            } else {
                if (placeHolder > 0) {
                    imageView.setImageResource(placeHolder);
                } else {
                    imageView.setImageDrawable(null);
                }

                this.task = new BitmapAsyncTask(this);
                task.executeOnExecutor(executor);
            }
        }

        @Override
        public boolean onPreDraw() {
            ImageView imageView = getImageView();

            if (imageView != null) {
                imageView.getViewTreeObserver().removeOnPreDrawListener(this);

                if (imageView.getViewTreeObserver().isAlive()) {
                    this.height = imageView.getHeight();
                    this.width = imageView.getWidth();
                    execute();
                }
            }

            return true;
        }

        /**
         * Returns the memory cache key for the request.
         *
         * @return The memory cache key.
         */
        String getCacheKey() {
            return imageUrl + ",size(" + width + "x" + height + ")";
        }
    }

    /**
     * Bitmap task to fetch the bitmap.
     */
    private class BitmapAsyncTask extends AsyncTask<Void, Void, BitmapDrawable> {
        private final Request request;

        BitmapAsyncTask(Request request) {
            this.request = request;
        }

        @Override
        protected BitmapDrawable doInBackground(Void... params) {
            installCache();

            if (request.imageUrl == null) {
                return null;
            }

            try {
                Bitmap bitmap = BitmapUtils.fetchScaledBitmap(context, new URL(request.imageUrl), request.width, request.height);
                if (bitmap != null) {
                    BitmapDrawable bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
                    memoryCache.put(request.getCacheKey(), bitmapDrawable);
                    return bitmapDrawable;
                }
            } catch (IOException e) {
                Logger.debug("Unable to fetch bitmap: " + request.imageUrl);
            }

            return null;
        }

        @Override
        protected void onPostExecute(BitmapDrawable bitmapDrawable) {
            final ImageView imageView = request.getImageView();
            if (bitmapDrawable != null && imageView != null) {
                // Transition drawable with a transparent drawable and the final drawable
                TransitionDrawable td = new TransitionDrawable(new Drawable[] {
                        new ColorDrawable(ContextCompat.getColor(context, android.R.color.transparent)),
                        bitmapDrawable
                });
                imageView.setImageDrawable(td);
                td.startTransition(FADE_IN_TIME_MS);
            }
        }

        /**
         * Installs a HttpResponseCache if a cache is not already installed.
         */
        private void installCache() {
            // URL Cache
            File cacheDir = new File(context.getApplicationContext().getCacheDir(), CACHE_DIR);
            if (!cacheDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();
            }

            if (HttpResponseCache.getInstalled() == null) {
                try {
                    HttpResponseCache.install(cacheDir, DISK_CACHE_SIZE);
                } catch (IOException e) {
                    Logger.error("Unable to install image loader cache");
                }
            }
        }
    }
}
