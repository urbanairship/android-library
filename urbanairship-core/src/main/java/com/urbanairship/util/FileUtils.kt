/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.WorkerThread
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.util.ConnectionUtils.openSecureConnection
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

/**
 * File utility methods.
 */
public object FileUtils {

    private const val NETWORK_TIMEOUT_MS = 2000
    private const val BUFFER_SIZE = 1024

    /**
     * Deletes a file and/or folder recursively.
     *
     * @param file The file to delete.
     * @return `true` if the file was deleted, otherwise `false`.
     */
    public fun deleteRecursively(file: File): Boolean {
        if (!file.exists()) {
            return false
        }

        if (!file.isDirectory) {
            return file.delete()
        }

        file.listFiles()?.forEach {
            if (!deleteRecursively(it)) {
                return false
            }
        }

        return file.delete()
    }

    /**
     * Downloads a file to disk.
     *
     * @param url The URL image.
     * @param file The file path where the image will be downloaded.
     * @return The download result.
     */
    @JvmStatic
    @WorkerThread
    public fun downloadFile(url: URL, file: File): DownloadResult {
        UALog.v("Downloading file from: $url to: ${file.absolutePath}")

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        var conn: URLConnection? = null

        try {
            conn = openSecureConnection(UAirship.getApplicationContext(), url)
            conn.connectTimeout = NETWORK_TIMEOUT_MS
            conn.useCaches = true

            var statusCode = 0

            if (conn is HttpURLConnection) {
                statusCode = conn.responseCode
                if (!UAHttpStatusUtil.inSuccessRange(statusCode)) {
                    return DownloadResult(false, statusCode)
                }
            }

            inputStream = conn.getInputStream()
            if (inputStream != null) {
                outputStream = FileOutputStream(file)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.close()
                inputStream.close()

                return DownloadResult(true, statusCode)
            }

            return DownloadResult(false, statusCode)
        } catch (e: Exception) {
            // the file may have been partially created - delete it
            file.delete()
            UALog.e(e, "Failed to download file from: %s", url)
            return DownloadResult(false, -1)
        } finally {
            val closeables = listOf(inputStream, outputStream)
                .mapNotNull { it }
                .toTypedArray()

            endRequest(conn, *closeables)
        }
    }

    /**
     * Helper method to end a connection request and any associated closeables.
     *
     * @param connection The connection.
     * @param closeables Closeables.
     */
    private fun endRequest(connection: URLConnection?, vararg closeables: Closeable) {
        closeables.forEach {
            try {
                it.close()
            } catch (e: Exception) {
                UALog.e(e)
            }
        }

        if (connection is HttpURLConnection) {
            // Any error response will generate an error stream so we need to make sure it
            // is closed or we will leak resources.
            if (connection.errorStream != null) {
                try {
                    connection.errorStream.close()
                } catch (e: Exception) {
                    UALog.e(e)
                }
            }

            connection.disconnect()
        }
    }

    /**
     * Result for downloading a file.
     */
    public class DownloadResult internal constructor(
        /**
         * If file downloaded successfully or not.
         */
        @JvmField public val isSuccess: Boolean,
        /**
         * The status code if available.
         */
        public val statusCode: Int
    )
}
