package com.urbanairship.iam.assets

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import com.urbanairship.UALog
import com.urbanairship.util.FileUtils
import java.io.File
import java.io.IOException
import java.net.URI

internal class DefaultAssetFileManager(
    context: Context,
    rootFolder: String = CACHE_DIRECTORY
): AssetFileManagerInterface {

    private val rootFolder = File(context.cacheDir, rootFolder)
    private val storageManager: StorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

    @Throws(IOException::class)
    override fun getRootDirectory(): URI {
        return ensureRootCacheDirectory()
    }

    @Throws(IOException::class)
    override fun ensureCacheDirectory(identifier: String): URI {
        ensureRootCacheDirectory()
        val file = File(rootFolder, identifier)
        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw IOException("Failed to create cache sub-folder $identifier")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            storageManager.setCacheBehaviorGroup(file, true)
        }
        return file.toURI()
    }

    @Throws(IOException::class)
    override fun assetItemExists(cacheURI: URI): Boolean {
        return File(cacheURI.path).exists()
    }

    @Throws(IOException::class, SecurityException::class)
    override fun moveAsset(from: URI, to: URI) {
        val fromFile = File(from.path)
        if (!fromFile.exists()) {
            throw IOException("can't find file at $from")
        }

        val toFile = File(to.path)
        if (toFile.exists()) {
            toFile.delete()
        }

        if (!fromFile.renameTo(toFile)) {
            copy(fromFile, toFile)
            fromFile.delete()
        }
    }

    override fun clearAssets(identifier: String) {
        FileUtils.deleteRecursively(File(rootFolder, identifier))
    }

    @Throws(IOException::class)
    private fun ensureRootCacheDirectory(): URI {
        if (!rootFolder.exists()) {
            if (!rootFolder.mkdirs()) {
                throw IOException("Failed to create cache folder: ${rootFolder.path}")
            }
        }

        return rootFolder.toURI()
    }

    private fun copy(from: File, to: File) {
        try {
            from.copyTo(to, overwrite = true)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to copy asset file from '$from' to '$to'" }
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "com.urbanairship.iam.assets"
    }
}
