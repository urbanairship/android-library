/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * A class containing utility methods related to bitmaps.
 */
public class BitmapUtils {

    private final static int NETWORK_TIMEOUT_MS = 2000;
    private final static int BUFFER_SIZE = 1024;

    /**
     * Create a scaled bitmap.
     * @param context The application context.
     * @param url The URL image.
     * @param reqWidth The requested width of the image.
     * @param reqHeight The requested height of the image.
     * @return The scaled bitmap.
     * @throws IOException
     */
    public static Bitmap fetchScaledBitmap(@NonNull Context context, @NonNull URL url, int reqWidth, int reqHeight) throws IOException {
        Logger.verbose("BitmapUtils - Fetching image from: " + url);

        File outputFile = File.createTempFile("ua_", ".temp", context.getCacheDir());
        Logger.verbose("BitmapUtils - Created temp file: " + outputFile);

        if (!downloadFile(url, outputFile)) {
            Logger.verbose("BitmapUtils - Failed to fetch image from: " + url);
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(outputFile.getAbsolutePath(), options);

        int width = options.outWidth;
        int height = options.outHeight;

        options.inSampleSize = calculateInSampleSize(width, height, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(outputFile.getAbsolutePath(), options);

        if (outputFile.delete()) {
            Logger.verbose("BitmapUtils - Deleted temp file: " + outputFile);
        } else {
            Logger.verbose("BitmapUtils - Failed to delete temp file: " + outputFile);
        }

        Logger.debug(String.format("BitmapUtils - Fetched image from: %s. Original image size: %dx%d. Requested image size: %dx%d. Bitmap size: %dx%d. SampleSize: %d",
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

    /**
     * Downloads a file to disk.
     * @param url The URL image.
     * @param file The file path where the image will be downloaded.
     * @return <code>true</code> if file was downloaded, <code>false</code> otherwise.
     * @throws IOException
     */
    private static boolean downloadFile(@NonNull URL url, @NonNull File file) throws IOException {
        Logger.verbose("Downloading file from: " + url + " to: " + file.getAbsolutePath());

        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setUseCaches(true);
            inputStream = conn.getInputStream();

            if (conn instanceof HttpURLConnection && !UAHttpStatusUtil.inSuccessRange(((HttpURLConnection) conn).getResponseCode())) {
                Logger.warn("Unable to download file from URL. Received response code: " + ((HttpURLConnection) conn).getResponseCode());
                return false;
            }

            if (inputStream != null) {
                outputStream = new FileOutputStream(file);

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                return true;
            }
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }

            if (inputStream != null) {
                inputStream.close();
            }
        }

        return false;
    }
}
