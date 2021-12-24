/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.webkit.URLUtil;

import com.urbanairship.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;

/**
 * A class containing utility methods related to bitmaps.
 */
public final class ImageUtils {

    private ImageUtils() {}

    /**
     * Drawable result.
     */
    public static final class DrawableResult {

        /**
         * The drawable.
         */
        public final Drawable drawable;

        /**
         * The size in bytes.
         */
        public final long bytes;

        private DrawableResult(Drawable drawable, long bytes) {
            this.drawable = drawable;
            this.bytes = bytes;
        }

    }

    /**
     * Fetches a drawable from an image path.
     *
     * @param context The application context.
     * @param url The URL.
     * @param reqWidth The requested width of the image.
     * @param reqHeight The requested height of the image.
     * @return The result or null if the file was unable to be downloaded.
     * @throws IOException if file fails to be created.
     */
    @Nullable
    public static DrawableResult fetchScaledDrawable(@NonNull Context context, @NonNull URL url, int reqWidth, int reqHeight) throws IOException {
        return fetchScaledDrawable(context, url, reqWidth, reqHeight, -1, -1);
    }

    /**
     * Fetches a drawable from an image path, using the supplied fallback dimensions if the {@code ImageView} reports
     * a width or height of zero. Setting the fallback dimensions to {@code -1} will automatically calculate zero
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
    @Nullable
    public static DrawableResult fetchScaledDrawable(
        @NonNull Context context,
        @NonNull URL url,
        int reqWidth,
        int reqHeight,
        int fallbackWidth,
        int fallbackHeight
    ) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Bitmap bitmap = ImageUtils.fetchScaledBitmap(context, url, reqWidth, reqHeight, fallbackWidth, fallbackHeight);

            if (bitmap == null) {
                return null;
            }

            Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            return new DrawableResult(drawable, bitmap.getByteCount());
        } else {
            return fetchImage(context, url, imageFile -> {
                ImageDecoder.Source source = ImageDecoder.createSource(imageFile);
                Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, source1) -> {
                    int sourceWidth = info.getSize().getWidth();
                    int sourceHeight = info.getSize().getHeight();
                    Size target = calculateTargetSize(
                        sourceWidth, sourceHeight, reqWidth, reqHeight, fallbackWidth, fallbackHeight);

                    decoder.setTargetSampleSize(calculateInSampleSize(sourceWidth, sourceHeight, target.width, target.height));
                });

                long byteCount;
                if (drawable instanceof BitmapDrawable) {
                    byteCount = ((BitmapDrawable) drawable).getBitmap().getByteCount();
                } else {
                    byteCount = imageFile.length();
                }

                return new DrawableResult(drawable, byteCount);
            });
        }
    }


    /**
     * Create a scaled bitmap.
     *
     * @param context The application context.
     * @param url The URL image.
     * @param reqWidth The requested width of the image.
     * @param reqHeight The requested height of the image.
     * @return The scaled bitmap.
     * @throws IOException if file fails to be created.
     */
    @Nullable
    public static Bitmap fetchScaledBitmap(@NonNull Context context, @NonNull URL url, int reqWidth, int reqHeight) throws IOException {
        return fetchScaledBitmap(context, url, reqWidth, reqHeight, -1, -1);
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
    @Nullable
    public static Bitmap fetchScaledBitmap(@NonNull Context context, @NonNull URL url, int reqWidth, int reqHeight, int fallbackWidth, int fallbackHeight) throws IOException {
        Bitmap bitmap = fetchImage(context, url, imageFile -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;

                BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

                int sourceWidth = options.outWidth;
                int sourceHeight = options.outHeight;
                Size target = calculateTargetSize(sourceWidth, sourceHeight, reqWidth, reqHeight, fallbackWidth, fallbackHeight);

                options.inSampleSize = calculateInSampleSize(sourceWidth, sourceHeight, target.width, target.height);
                options.inJustDecodeBounds = false;

                return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
            } else {

                ImageDecoder.Source source = ImageDecoder.createSource(imageFile);

                return ImageDecoder.decodeBitmap(source, (decoder, info, source1) -> {
                    int sourceWidth = info.getSize().getWidth();
                    int sourceHeight = info.getSize().getHeight();
                    Size target = calculateTargetSize(sourceWidth, sourceHeight, reqWidth, reqHeight, fallbackWidth, fallbackHeight);

                    decoder.setTargetSampleSize(calculateInSampleSize(sourceWidth, sourceHeight, target.width, target.height));
                });
            }
        });

        if (bitmap != null) {
            Logger.debug("Fetched image from: %s. Original image size: %dx%d. Requested image size: %dx%d. Bitmap size: %dx%d.", url, reqWidth, reqHeight, reqWidth, reqHeight, bitmap.getWidth(), bitmap.getHeight());
        }

        return bitmap;
    }

    /**
     * Calculate the largest inSampleSize value that is a power of 2 and keeps both
     * height and width larger than the requested height and width.
     * <p>
     * Taken from http://developer.android.com/training/displaying-bitmaps/load-bitmap.html.
     *
     * @param width The width of the image.
     * @param height The height of the image.
     * @param reqWidth The requested width of the image.
     * @param reqHeight The requested height of the image.
     * @return The calculated inSampleSize.
     */
    public static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            int halfHeight = height / 2;
            int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
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
    @NonNull
    public static Size calculateTargetSize(int width, int height, int reqWidth, int reqHeight, int fallbackWidth, int fallbackHeight) {
        if (width == 0 || height == 0) {
            throw new IllegalArgumentException("Failed to calculate target size! width and height must be greater than zero.");
        }
        if (reqWidth == 0 && reqHeight == 0) {
            throw new IllegalArgumentException("Failed to calculate target size! reqWidth and reqHeight may not both be zero.");
        }

        int targetWidth;
        int targetHeight;

        if (reqWidth == 0) {
            targetWidth = fallbackWidth > 0
                ? fallbackWidth
                : (int) (reqHeight * (width / (double) height));
        } else {
            targetWidth = reqWidth;
        }

        if (reqHeight == 0) {
            targetHeight = fallbackHeight > 0
                ? fallbackHeight
                : (int) (reqWidth * (height / (double) width));
        } else {
            targetHeight = reqHeight;
        }

        return new Size(targetWidth, targetHeight);
    }

    /**
     * Interface used in the helper method {@link #fetchImage(Context, URL, ImageProcessor)} to convert
     * the file to a drawable or bitmap.
     *
     * @param <T> The result.
     */
    private interface ImageProcessor<T> {

        /**
         * Called to process the file.
         *
         * @param imageFile The image file.
         * @return The result.
         * @throws IOException
         */
        T onProcessFile(File imageFile) throws IOException;

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
    private static <T> T fetchImage(@NonNull Context context, @NonNull URL url, ImageProcessor<T> imageProcessor) throws IOException {
        Logger.verbose("Fetching image from: %s", url);

        boolean deleteFile = false;
        File imageFile = null;

        try {
            if (URLUtil.isFileUrl(url.toString())) {
                imageFile = new File(url.toURI());
            } else {
                imageFile = File.createTempFile("ua_", ".temp", context.getCacheDir());
                deleteFile = true;

                if (!FileUtils.downloadFile(url, imageFile).isSuccess) {
                    Logger.verbose("Failed to fetch image from: %s", url);
                    return null;
                }
            }

            return imageProcessor.onProcessFile(imageFile);
        } catch (URISyntaxException e) {
            Logger.error("ImageUtils - Invalid URL: %s ", url);
            return null;
        } finally {
            if (deleteFile && imageFile != null) {
                if (imageFile.delete()) {
                    Logger.verbose("Deleted temp file: %s", imageFile);
                } else {
                    Logger.verbose("Failed to delete temp file: %s", imageFile);
                }
            }
        }
    }

    /**
     * Immutable wrapper for a width and height, used as a stand-in for android.util.Size which is
     * only supported on API 21+.
     */
    static class Size {
        final int width;
        final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Size size = (Size) o;
            return width == size.width && height == size.height;
        }

        @Override
        public int hashCode() {
            return ObjectsCompat.hash(width, height);
        }
    }
}
