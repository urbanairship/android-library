/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.util.ImageUtils;

import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Notification Utils.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationUtils {

    private final static long BIG_PICTURE_TIMEOUT_SECONDS = 7;

    private final static int BIG_IMAGE_HEIGHT_DP = 240;
    private final static double BIG_IMAGE_SCREEN_WIDTH_PERCENT = .75;

    /**
     * Fetches a big image for a given URL. Attempts to sample the image down to a reasonable size
     * before loading into memory.
     *
     * @param url The image URL.
     * @return The bitmap, or null if it failed to be fetched.
     */
    @Nullable
    public static Bitmap fetchBigImage(@NonNull final Context context, @NonNull final URL url) {

        Logger.debug("Fetching notification image at URL: %s", url);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();

        // Since notifications do not take up the entire screen, request 3/4 the longest device dimension
        final int reqWidth = (int) (Math.max(dm.widthPixels, dm.heightPixels) * BIG_IMAGE_SCREEN_WIDTH_PERCENT);

        // Big images have a max height of 240dp
        final int reqHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BIG_IMAGE_HEIGHT_DP, dm);

        Future<Bitmap> future = AirshipExecutors.threadPoolExecutor().submit(new Callable<Bitmap>() {
            @Nullable
            @Override
            public Bitmap call() throws Exception {
                return ImageUtils.fetchScaledBitmap(context, url, reqWidth, reqHeight);
            }
        });

        try {
            return future.get(BIG_PICTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            Logger.error("Failed to create big picture style, unable to fetch image: %s", e);
        } catch (TimeoutException e) {
            future.cancel(true);
            Logger.error("Big picture took longer than %s seconds to fetch.", BIG_PICTURE_TIMEOUT_SECONDS);
        }

        return null;
    }
}
