/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.urbanairship.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * File utility methods.
 */
public abstract class FileUtils {

    private final static int NETWORK_TIMEOUT_MS = 2000;
    private final static int BUFFER_SIZE = 1024;

    /**
     * Deletes a file and/or folder recursively.
     *
     * @param file The file to delete.
     * @return {@code true} if the file was deleted, otherwise {@code false}.
     */
    public static boolean deleteRecursively(File file) {
        if (!file.exists()) {
            return false;
        }

        if (!file.isDirectory()) {
            return file.delete();
        }


        File[] children = file.listFiles();
        if (children != null) {
            for (File child : file.listFiles()) {
                if (!deleteRecursively(child)) {
                    return false;
                }
            }
        }

        return file.delete();
    }

    /**
     * Downloads a file to disk.
     *
     * @param url The URL image.
     * @param file The file path where the image will be downloaded.
     * @return <code>true</code> if file was downloaded, <code>false</code> otherwise.
     * @throws IOException
     */
    @WorkerThread
    public static boolean downloadFile(@NonNull URL url, @NonNull File file) throws IOException {
        Logger.verbose("Downloading file from: " + url + " to: " + file.getAbsolutePath());

        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        URLConnection conn = null;

        try {
            conn = url.openConnection();
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

            if (conn != null && conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }
        }

        return false;
    }
}
