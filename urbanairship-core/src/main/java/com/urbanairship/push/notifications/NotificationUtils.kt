/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.content.Context
import android.graphics.Bitmap
import android.util.TypedValue
import com.urbanairship.AirshipExecutors
import com.urbanairship.UALog
import com.urbanairship.util.ImageUtils
import java.net.URL
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.max

/**
 * Notification Utils.
 *
 * @hide
 */
internal object NotificationUtils {

    private const val BIG_PICTURE_TIMEOUT_SECONDS: Long = 7 // seconds

    private const val BIG_IMAGE_HEIGHT_DP = 240
    private const val BIG_IMAGE_SCREEN_WIDTH_PERCENT = .75

    /**
     * Fetches a big image for a given URL. Attempts to sample the image down to a reasonable size
     * before loading into memory.
     *
     * @param url The image URL.
     * @return The bitmap, or null if it failed to be fetched.
     */
    fun fetchBigImage(context: Context, url: URL): Bitmap? {
        UALog.d("Fetching notification image at URL: %s", url)
        val dm = context.resources.displayMetrics

        // Since notifications do not take up the entire screen, request 3/4 the longest device dimension
        val reqWidth = (max(dm.widthPixels, dm.heightPixels) * BIG_IMAGE_SCREEN_WIDTH_PERCENT).toInt()

        // Big images have a max height of 240dp
        val reqHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BIG_IMAGE_HEIGHT_DP.toFloat(), dm).toInt()

        val future = AirshipExecutors.threadPoolExecutor()
            .submit<Bitmap> {
                ImageUtils.fetchScaledBitmap(context, url, reqWidth, reqHeight)
            }

        try {
            return future[BIG_PICTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS]
        } catch (e: InterruptedException) {
            UALog.e("Failed to create big picture style, unable to fetch image: %s", e)
        } catch (e: ExecutionException) {
            UALog.e("Failed to create big picture style, unable to fetch image: %s", e)
        } catch (e: TimeoutException) {
            future.cancel(true)
            UALog.e("Big picture took longer than %s seconds to fetch.", BIG_PICTURE_TIMEOUT_SECONDS)
        }

        return null
    }
}
