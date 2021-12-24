/* Copyright Airship and Contributors */

package com.urbanairship.images;

import android.content.Context;
import android.widget.ImageView;

import java.util.Map;
import java.util.WeakHashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Asynchronous bitmap loader for image views.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DefaultImageLoader implements ImageLoader {

    private final Map<ImageView, ImageRequest> requestMap;
    private final ImageCache imageCache;

    /**
     * Creates an ImageLoader.
     *
     * @param context The application context.
     */
    public DefaultImageLoader(@NonNull Context context) {
        this.requestMap = new WeakHashMap<>();
        this.imageCache = new ImageCache(context);
    }

    /**
     * Cancels a request.
     *
     * @param imageView The imageView.
     */
    private void cancelRequest(@Nullable ImageView imageView) {
        if (imageView == null) {
            return;
        }

        ImageRequest request = requestMap.remove(imageView);
        if (request != null) {
            request.cancel();
        }
    }

    @Override
    public void load(@NonNull Context context, @NonNull ImageView imageView, @NonNull ImageRequestOptions imageRequestOptions) {
        cancelRequest(imageView);

        ImageRequest request = new ImageRequest(context, imageCache, imageView, imageRequestOptions) {
            @Override
            void onFinish(ImageView imageView) {
                if (imageView != null) {
                    requestMap.remove(imageView);

                    ImageLoadedCallback callback = imageRequestOptions.getCallback();
                    if (callback != null) {
                        callback.onImageLoaded();
                    }
                }
            }
        };

        requestMap.put(imageView, request);
        request.execute();
    }

}
