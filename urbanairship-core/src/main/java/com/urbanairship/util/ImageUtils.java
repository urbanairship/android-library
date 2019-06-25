/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.webkit.URLUtil;

import com.urbanairship.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * A class containing utility methods related to bitmaps.
 */
public class ImageUtils {

    /**
     * Drawable result.
     */
    public static class DrawableResult {

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
    public static DrawableResult fetchScaledDrawable(@NonNull Context context, @NonNull URL url, final int reqWidth, final int reqHeight) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Bitmap bitmap = ImageUtils.fetchScaledBitmap(context, url, reqWidth, reqHeight);

            if (bitmap == null) {
                return null;
            }

            Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            return new DrawableResult(drawable, bitmap.getByteCount());
        } else {
            return fetchImage(context, url, new ImageProcessor<DrawableResult>() {
                @Override
                public DrawableResult onProcessFile(File imageFile) throws IOException {
                    ImageDecoder.Source source = ImageDecoder.createSource(imageFile);
                    Drawable drawable = ImageDecoder.decodeDrawable(source, new ImageDecoder.OnHeaderDecodedListener() {
                        @RequiresApi(api = Build.VERSION_CODES.P)
                        @Override
                        public void onHeaderDecoded(@NonNull ImageDecoder decoder, @NonNull ImageDecoder.ImageInfo info, @NonNull ImageDecoder.Source source) {
                            decoder.setTargetSize(reqWidth, reqHeight);
                            decoder.setTargetSampleSize(calculateInSampleSize(info.getSize().getWidth(), info.getSize().getHeight(), reqWidth, reqHeight));
                        }
                    });

                    long byteCount;
                    if (drawable instanceof BitmapDrawable) {
                        byteCount = ((BitmapDrawable) drawable).getBitmap().getByteCount();
                    } else {
                        byteCount = imageFile.length();
                    }

                    return new DrawableResult(drawable, byteCount);
                }
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
    public static Bitmap fetchScaledBitmap(@NonNull Context context, @NonNull URL url, final int reqWidth, final int reqHeight) throws IOException {
        Bitmap bitmap = fetchImage(context, url, new ImageProcessor<Bitmap>() {
            @Override
            public Bitmap onProcessFile(File imageFile) throws IOException {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;

                    BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

                    int width = options.outWidth;
                    int height = options.outHeight;

                    options.inSampleSize = calculateInSampleSize(width, height, reqWidth, reqHeight);
                    options.inJustDecodeBounds = false;

                    return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                } else {

                    ImageDecoder.Source source = ImageDecoder.createSource(imageFile);
                    Bitmap bitmap = ImageDecoder.decodeBitmap(source, new ImageDecoder.OnHeaderDecodedListener() {
                        @RequiresApi(api = Build.VERSION_CODES.P)
                        @Override
                        public void onHeaderDecoded(@NonNull ImageDecoder decoder, @NonNull ImageDecoder.ImageInfo info, @NonNull ImageDecoder.Source source) {
                            decoder.setTargetSize(reqWidth, reqHeight);
                            decoder.setTargetSampleSize(calculateInSampleSize(info.getSize().getWidth(), info.getSize().getHeight(), reqWidth, reqHeight));
                        }
                    });

                    return bitmap;
                }
            }
        });

        if (bitmap != null) {
            Logger.debug("ImageUtils - Fetched image from: %s. Original image size: %dx%d. Requested image size: %dx%d. Bitmap size: %dx%d.",
                    url, reqWidth, reqHeight, reqWidth, reqHeight, bitmap.getWidth(), bitmap.getHeight());
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

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

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
        Logger.verbose("ImageUtils - Fetching image from: %s", url);

        boolean deleteFile = false;
        File imageFile = null;

        try {
            if (URLUtil.isFileUrl(url.toString())) {
                imageFile = new File(url.toURI());
            } else {
                imageFile = File.createTempFile("ua_", ".temp", context.getCacheDir());
                deleteFile = true;

                if (!FileUtils.downloadFile(url, imageFile).isSuccess) {
                    Logger.verbose("ImageUtils - Failed to fetch image from: %s", url);
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
                    Logger.verbose("ImageUtils - Deleted temp file: %s", imageFile);
                } else {
                    Logger.verbose("ImageUtils - Failed to delete temp file: %s", imageFile);
                }
            }
        }
    }

}
