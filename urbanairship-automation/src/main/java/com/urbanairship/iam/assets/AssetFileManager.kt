package com.urbanairship.iam.assets

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.urbanairship.UALog
import com.urbanairship.util.FileUtils
import java.io.File
import java.io.IOException

/**
 * Wrapper for the filesystem that is responsible for asset-caching related file and directory operations
 */
internal interface AssetFileManager {

    /**
     * Gets or creates the root directory
     */
    @Throws(IOException::class)
    fun getRootDirectory(): Uri

    /**
     * Gets or creates cache directory based on the root directory with the provided identifier (usually a schedule ID) and returns its full cache URI
     */
    @Throws(IOException::class)
    fun ensureCacheDirectory(identifier: String): File

    /**
     * Checks if asset file or directory exists at cache URI
     */
    @Throws(IOException::class)
    fun assetItemExists(cacheUri: Uri): Boolean

    /**
     * Moves the asset from a temporary URI to its asset cache directory
     */
    @Throws(IOException::class)
    fun moveAsset(from: Uri, to: Uri)

    /**
     * Clears all assets corresponding to the provided identifier
     */
    @Throws(IOException::class)
    fun clearAssets(identifier: String)
}


internal class DefaultAssetFileManager(
    context: Context,
    rootFolder: String = CACHE_DIRECTORY
): AssetFileManager {

    private val rootFolder = File(context.cacheDir, rootFolder)
    private val storageManager: StorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

    @Throws(IOException::class)
    override fun getRootDirectory(): Uri {
        return ensureRootCacheDirectory()
    }

    @Throws(IOException::class)
    override fun ensureCacheDirectory(identifier: String): File {
        ensureRootCacheDirectory()
        val subDir = File(rootFolder, identifier)
        if (!subDir.exists()) {
            if (!subDir.mkdirs()) {
                throw IOException("Failed to create cache sub-folder! $identifier")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                storageManager.setCacheBehaviorGroup(subDir, true)
            } catch (e: IOException) {
                UALog.e(e) { "Failed to set cache behavior group! $subDir" }
            }
        }
        return subDir
    }

    @Throws(IOException::class)
    override fun assetItemExists(cacheUri: Uri): Boolean {
        return cacheUri.toFile().exists()
    }

    @Throws(IOException::class)
    override fun moveAsset(from: Uri, to: Uri) {
        val fromFile = from.toFile()
        if (!fromFile.exists()) {
            throw IOException("can't find file at $from")
        }

        val toFile = to.toFile()
        if (toFile.exists()) {
            toFile.delete()
        }

        if (!fromFile.renameTo(toFile)) {
            try {
                fromFile.copyTo(toFile, overwrite = true)
                fromFile.delete()
            } catch (e: Exception) {
                UALog.e(e) { "Failed to copy asset file from '$from' to '$to'" }
                return
            }
        }

        UALog.v { "Moved asset file from '$from' to '$to'" }
    }

    override fun clearAssets(identifier: String) {
        FileUtils.deleteRecursively(File(rootFolder, identifier))
    }

    @Throws(IOException::class)
    private fun ensureRootCacheDirectory(): Uri {
        if (!rootFolder.exists()) {
            if (!rootFolder.mkdirs() && !rootFolder.exists()) {
                throw IOException("Failed to create cache folder: ${rootFolder.path}")
            }
        }

        return rootFolder.toUri()
    }

    companion object {
        private const val CACHE_DIRECTORY = "com.urbanairship.iam.assets"
    }
}
