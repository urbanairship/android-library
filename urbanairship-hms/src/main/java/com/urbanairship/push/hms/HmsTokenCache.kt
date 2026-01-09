package com.urbanairship.push.hms

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.urbanairship.UALog
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Caches the token to allow us to use the same access pattern for all versions of HMS devices.
 *
 * `HmsInstanceId#getToken` only returns the token if the EMUI version of the devices is 10 or newer.
 * Otherwise we have to rely on `HmsMessageService#onNewToken`. Since our SDK relies on a way to get
 * the token every time using some sort of `getToken` method, we cache the token locally and return it
 * in [HmsPushProvider.getRegistrationToken] if `HmsInstanceId#getToken` fails to return
 * a token.
 */
internal class HmsTokenCache @VisibleForTesting constructor() {

    private val lock = Any()
    private var token: String? = null

    operator fun get(context: Context): String? {
        synchronized(lock) {
            if (token == null) {
                token = readToken(context)
                UALog.v("HMS token from cache: $token")
            }
            return token
        }
    }

    operator fun set(context: Context, token: String?) {
        synchronized(lock) {
            if (token == get(context)) {
                return
            }
            this.token = token
            if (token != null) {
                writeToken(context, token)
                UALog.v("Cached HMS token %s", token)
            } else {
                deleteToken(context)
                UALog.v("Deleted cached HMS token")
            }
        }
    }

    private fun readToken(context: Context): String? {
        val file = File(ContextCompat.getNoBackupFilesDir(context), FILE_PATH)
        if (!file.exists()) {
            return null
        }

        var inputStream: FileInputStream? = null
        try {
            inputStream = FileInputStream(file)
            val data = ByteArray(file.length().toInt())
            inputStream.read(data)
            val token = String(data, charset("UTF-8"))
            return token
        } catch (e: Exception) {
            UALog.e(e, "Failed to read HMS token")
        } finally {
            closeQuietly(inputStream)
        }

        return null
    }

    private fun writeToken(context: Context, token: String) {
        val file = File(ContextCompat.getNoBackupFilesDir(context), FILE_PATH)
        val parent = file.parentFile

        if (parent == null || !parent.exists() && !parent.mkdirs()) {
            UALog.w("Unable to create HMS token cache.")
            return
        }

        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(file)
            fileOutputStream.write(token.toByteArray())
            fileOutputStream.close()
        } catch (e: Exception) {
            UALog.e(e, "Failed to write HMS token.")
        } finally {
            closeQuietly(fileOutputStream)
        }
    }

    private fun closeQuietly(outputStream: Closeable?) {
        val stream = outputStream ?: return

        try {
            stream.close()
        } catch (e: IOException) {
            UALog.e(e, "Failed to close stream.")
        }
    }

    private fun deleteToken(context: Context) {
        val file = File(ContextCompat.getNoBackupFilesDir(context), FILE_PATH)
        if (file.exists() && !file.delete()) {
            UALog.e("Failed to delete HMS token cache.")
        }
    }

    companion object {

        private const val FILE_PATH = "com.urbanairship.push.hms/token.txt"

        private val instance = HmsTokenCache()

        fun shared(): HmsTokenCache {
            return instance
        }
    }
}
