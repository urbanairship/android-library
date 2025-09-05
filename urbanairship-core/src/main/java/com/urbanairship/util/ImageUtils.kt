/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.webkit.URLUtil
import androidx.core.util.ObjectsCompat
import com.urbanairship.UALog
import java.io.File
import java.io.IOException
import java.net.URISyntaxException
import java.net.URL

/**
 * A class containing utility methods related to bitmaps.
 */
internal object ImageUtils {

    /**
     * Fetches a drawable from an image path, using the supplied fallback dimensions if the `ImageView` reports
     * a width or height of zero. Setting the fallback dimensions to `-1` will automatically calculate zero
     * dimensions based on the aspect ratio of the image.
     *
     * @param context The application context.
     * @param url The URL.
     * @param reqWidth The requested width of the image.
     * @param reqHeight The requested height of the image.
     * @param fallbackWidth The width dimension to be used if the ImageView reports a width of zero.
     * @param fallbackHeight The height dimension to be used if the ImageView reports a height of zero.
     * @return The result or null if the file was unable to be downloaded.
     * @throws IOException if file fails to be created.
     */
    @Throws(IOException::class)
    fun fetchScaledDrawable(
        context: Context,
        url: URL,
        reqWidth: Int,
        reqHeight: Int,
        fallbackWidth: Int = -1,
        fallbackHeight: Int = -1
    ): DrawableResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val bitmap = fetchScaledBitmap(context, url, reqWidth, reqHeight, fallbackWidth, fallbackHeight)
                ?: return null

            val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
            return DrawableResult(drawable, bitmap.byteCount.toLong())
        }

        return fetchImage(
            context = context,
            url = url,
            imageProcessor = { imageFile ->
                val source = ImageDecoder.createSource(imageFile)
                val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    val sourceWidth = info.size.width
                    val sourceHeight = info.size.height
                    val target = calculateTargetSize(
                        sourceWidth,
                        sourceHeight,
                        reqWidth,
                        reqHeight,
                        fallbackWidth,
                        fallbackHeight
                    )
                    decoder.setTargetSampleSize(
                        calculateInSampleSize(
                            sourceWidth, sourceHeight, target.width, target.height
                        )
                    )
                }
                val byteCount = if (drawable is BitmapDrawable) {
                    drawable.bitmap.byteCount.toLong()
                } else {
                    imageFile.length()
                }
                DrawableResult(drawable, byteCount)
            })
    }

    /**
     * Create a scaled bitmap.
     *
     * @param context The application context.
     * @param url The URL image.
     * @param reqWidth The requested width of the image.
     * @param reqHeight The requested height of the image.
     * @param fallbackWidth The width dimension to be used if the ImageView reports a width of zero.
     * @param fallbackHeight The height dimension to be used if the ImageView reports a height of zero.
     * @return The scaled bitmap.
     * @throws IOException if file fails to be created.
     */
    @Throws(IOException::class)
    fun fetchScaledBitmap(
        context: Context,
        url: URL,
        reqWidth: Int,
        reqHeight: Int,
        fallbackWidth: Int = -1,
        fallbackHeight: Int = -1
    ): Bitmap? {
        val bitmap = fetchImage<Bitmap>(
            context = context,
            url = url,
            imageProcessor = { imageFile ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true

                    BitmapFactory.decodeFile(imageFile.absolutePath, options)

                    val sourceWidth = options.outWidth
                    val sourceHeight = options.outHeight
                    val target = calculateTargetSize(
                        sourceWidth, sourceHeight, reqWidth, reqHeight, fallbackWidth, fallbackHeight
                    )

                    options.inSampleSize = calculateInSampleSize(
                        sourceWidth, sourceHeight, target.width, target.height
                    )
                    options.inJustDecodeBounds = false

                    return@fetchImage BitmapFactory.decodeFile(imageFile.absolutePath, options)
                }

                val source = ImageDecoder.createSource(imageFile)

                return@fetchImage ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val sourceWidth = info.size.width
                    val sourceHeight = info.size.height
                    val target = calculateTargetSize(
                        sourceWidth,
                        sourceHeight,
                        reqWidth,
                        reqHeight,
                        fallbackWidth,
                        fallbackHeight
                    )
                    decoder.setTargetSampleSize(
                        calculateInSampleSize(
                            sourceWidth, sourceHeight, target.width, target.height
                        )
                    )
                }
            })

        if (bitmap != null) {
            UALog.d(
                "Fetched image from: %s. Original image size: %dx%d. Requested image size: %dx%d. Bitmap size: %dx%d.",
                url,
                reqWidth,
                reqHeight,
                reqWidth,
                reqHeight,
                bitmap.width,
                bitmap.height
            )
        }

        return bitmap
    }

    /**
     * Calculate the largest inSampleSize value that is a power of 2 and keeps both
     * height and width larger than the requested height and width.
     *
     *
     * Taken from http://developer.android.com/training/displaying-bitmaps/load-bitmap.html.
     *
     * @param width The width of the image.
     * @param height The height of the image.
     * @param reqWidth The requested width of the image.
     * @param reqHeight The requested height of the image.
     * @return The calculated inSampleSize.
     */
    fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight || (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Calculates a target size based on the original image aspect ratio if either the request width
     * or request height are 0, otherwise returns the request size.
     *
     * @param width The width of the image.
     * @param height The height of the image.
     * @param reqWidth The requested width of the image.
     * @param reqHeight The requested height of the image.
     * @return The target Size.
     */
    fun calculateTargetSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
        fallbackWidth: Int,
        fallbackHeight: Int
    ): Size {
        require(!(width == 0 || height == 0)) { "Failed to calculate target size! width and height must be greater than zero." }
        require(!(reqWidth == 0 && reqHeight == 0)) { "Failed to calculate target size! reqWidth and reqHeight may not both be zero." }

        val targetWidth = if (reqWidth == 0) {
            if (fallbackWidth > 0) fallbackWidth
            else (reqHeight * (width / height.toDouble())).toInt()
        } else {
            reqWidth
        }

        val targetHeight = if (reqHeight == 0) {
            if (fallbackHeight > 0) fallbackHeight
            else (reqWidth * (height / width.toDouble())).toInt()
        } else {
            reqHeight
        }

        return Size(targetWidth, targetHeight)
    }

    /**
     * Helper method to fetch and process an image file.
     *
     * @param context The context.
     * @param url The url.
     * @param imageProcessor The image processor.
     * @return The result.
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun <T> fetchImage(context: Context, url: URL, imageProcessor: ImageProcessor<T>): T? {
        UALog.v("Fetching image from: %s", url)

        var deleteFile = false
        var imageFile: File? = null

        try {
            val file: File
            if (URLUtil.isFileUrl(url.toString())) {
                file = File(url.toURI())
            } else {
                file = File.createTempFile("ua_", ".temp", context.cacheDir)
                deleteFile = true

                if (!FileUtils.downloadFile(url, file).isSuccess) {
                    UALog.v("Failed to fetch image from: %s", url)
                    return null
                }
            }

            imageFile = file

            return imageProcessor.onProcessFile(file)
        } catch (e: URISyntaxException) {
            UALog.e("ImageUtils - Invalid URL: %s ", url)
            return null
        } finally {
            if (deleteFile && imageFile != null) {
                if (imageFile.delete()) {
                    UALog.v("Deleted temp file: %s", imageFile)
                } else {
                    UALog.v("Failed to delete temp file: %s", imageFile)
                }
            }
        }
    }

    /**
     * Drawable result.
     */
    class DrawableResult(
        /**
         * The drawable.
         */
        val drawable: Drawable,
        /**
         * The size in bytes.
         */
        val bytes: Long
    )

    /**
     * Interface used in the helper method [.fetchImage] to convert
     * the file to a drawable or bitmap.
     *
     * @param <T> The result.
    </T> */
    private fun interface ImageProcessor<T> {

        /**
         * Called to process the file.
         *
         * @param imageFile The image file.
         * @return The result.
         * @throws IOException
         */
        @Throws(IOException::class)
        fun onProcessFile(imageFile: File): T
    }

    /**
     * Immutable wrapper for a width and height, used as a stand-in for android.util.Size which is
     * only supported on API 21+.
     */
    class Size(val width: Int, val height: Int) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val size = other as Size
            return width == size.width && height == size.height
        }

        override fun hashCode(): Int {
            return ObjectsCompat.hash(width, height)
        }
    }
}
