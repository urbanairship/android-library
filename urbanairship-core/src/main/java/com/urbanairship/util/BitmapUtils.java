/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.webkit.URLUtil;

import com.urbanairship.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;

/**
 * A class containing utility methods related to bitmaps.
 */
public class BitmapUtils {

    /**
     * Create a scaled bitmap.
     *
     * @param context The application context.
     * @param url The URL image.
     * @param reqWidth The requested width of the image.
     * @param reqHeight The requested height of the image.
     * @return The scaled bitmap.
     * @throws IOException
     */
    @Nullable
    public static Bitmap fetchScaledBitmap(@NonNull Context context, @NonNull URL url, int reqWidth, int reqHeight) throws IOException {
        Logger.verbose("BitmapUtils - Fetching image from: " + url);

        boolean deleteFile = false;
        File imageFile = null;
        if (URLUtil.isFileUrl(url.toString())) {
            deleteFile = false;
            try {
                imageFile = new File(url.toURI());
            } catch (URISyntaxException e) {
                Logger.error("BitmapUtils - Invalid URL: " + url);
            }
        }

        if (imageFile == null) {
            imageFile = File.createTempFile("ua_", ".temp", context.getCacheDir());
            deleteFile = true;
            Logger.verbose("BitmapUtils - Created temp file: " + imageFile);

            if (!FileUtils.downloadFile(url, imageFile).isSuccess) {
                Logger.verbose("BitmapUtils - Failed to fetch image from: " + url);
                return null;
            }
        }


        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

        int width = options.outWidth;
        int height = options.outHeight;

        options.inSampleSize = calculateInSampleSize(width, height, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

        if (deleteFile) {
            if (imageFile.delete()) {
                Logger.verbose("BitmapUtils - Deleted temp file: " + imageFile);
            } else {
                Logger.verbose("BitmapUtils - Failed to delete temp file: " + imageFile);
            }
        }

        if (bitmap == null) {
            Logger.error("BitmapUtils - Failed to create bitmap for URL: " + url);
            return null;
        }

        Logger.debug(String.format(Locale.US, "BitmapUtils - Fetched image from: %s. Original image size: %dx%d. Requested image size: %dx%d. Bitmap size: %dx%d. SampleSize: %d",
                url, width, height, reqWidth, reqHeight, bitmap.getWidth(), bitmap.getHeight(), options.inSampleSize));

        return bitmap;
    }

    /**
     * Calculate the largest inSampleSize value that is a power of 2 and keeps both
     * height and width larger than the requested height and width.
     * <p/>
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


}
