/* Copyright Airship and Contributors */

package com.urbanairship.util;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

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
    public static boolean deleteRecursively(@NonNull File file) {
        if (!file.exists()) {
            return false;
        }

        if (!file.isDirectory()) {
            return file.delete();
        }

        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteRecursively(child)) {
                    return false;
                }
            }
        }

        return file.delete();
    }

    /**
     * Result for downloading a file.
     */
    public static class DownloadResult {

        /**
         * The status code if available.
         */
        public final int statusCode;

        /**
         * If file downloaded successfully or not.
         */
        public final boolean isSuccess;

        DownloadResult(boolean isSuccess, int statusCode) {
            this.isSuccess = isSuccess;
            this.statusCode = statusCode;
        }

    }

    /**
     * Downloads a file to disk.
     *
     * @param url The URL image.
     * @param file The file path where the image will be downloaded.
     * @return The download result.
     * @throws IOException if output steam read or write operation fails.
     */
    @NonNull
    @WorkerThread
    public static DownloadResult downloadFile(@NonNull URL url, @NonNull File file) throws IOException {
        Logger.verbose("Downloading file from: %s to: %s", url, file.getAbsolutePath());

        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        URLConnection conn = null;

        try {

            conn = ConnectionUtils.openSecureConnection(UAirship.getApplicationContext(), url);
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setUseCaches(true);


            int statusCode = 0;

            if (conn instanceof HttpURLConnection) {
                statusCode = ((HttpURLConnection) conn).getResponseCode();
                if (!UAHttpStatusUtil.inSuccessRange(statusCode)) {
                    return new DownloadResult(false, statusCode);
                }
            }

            inputStream = conn.getInputStream();
            if (inputStream != null) {
                outputStream = new FileOutputStream(file);

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                return new DownloadResult(true, statusCode);
            }

            return new DownloadResult(false, statusCode);
        } catch (IOException | IllegalStateException e) {
            // the file may have been partially created - delete it
            file.delete();
            Logger.error(e, "Failed to download file from: %s", url);
            return new DownloadResult(false, -1);
        } finally {
            endRequest(conn, inputStream, outputStream);
        }
    }

    /**
     * Helper method to end a connection request and any associated closeables.
     *
     * @param connection The connection.
     * @param closeables Closeables.
     */
    private static void endRequest(@Nullable URLConnection connection, @NonNull Closeable... closeables) {
        for (Closeable closeable : closeables) {
            if (closeable == null) {
                continue;
            }

            try {
                closeable.close();
            } catch (Exception e) {
                Logger.error(e);
            }
        }

        if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) connection;

            // Any error response will generate an error stream so we need to make sure it
            // is closed or we will leak resources.
            if (httpURLConnection.getErrorStream() != null) {
                try {
                    httpURLConnection.getErrorStream().close();
                } catch (Exception e) {
                    Logger.error(e);
                }
            }

            httpURLConnection.disconnect();
        }

    }

}
