package com.urbanairship.automation.rewrite.inappmessage.assets

import android.content.Context
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import java.io.IOException
import java.net.URI
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

/**
 * Wrapper for the download tasks that is responsible for downloading assets
 */
internal interface AssetDownloaderInterface {

    /**
     * Downloads the asset from a remote URL and returns its temporary local URI
     */
    fun downloadAsset(remoteURL: URL): Deferred<URI>
}

/**
 * Wrapper for the filesystem that is responsible for asset-caching related file and directory operations
 */
internal interface AssetFileManagerInterface {

    /**
     * Gets or creates the root directory
     */
    @Throws(IOException::class)
    fun getRootDirectory(): URI

    /**
     * Gets or creates cache directory based on the root directory with the provided identifier (usually a schedule ID) and returns its full cache URI
     */
    @Throws(IOException::class)
    fun ensureCacheDirectory(identifier: String): URI

    /**
     * Checks if asset file or directory exists at cache URI
     */
    @Throws(IOException::class)
    fun assetItemExists(cacheURI: URI): Boolean

    /**
     * Moves the asset from a temporary URI to its asset cache directory
     */
    @Throws(IOException::class)
    fun moveAsset(from: URI, to: URI)

    /**
     * Clears all assets corresponding to the provided identifier
     */
    @Throws(IOException::class)
    fun clearAssets(identifier: String)
}

internal interface AssetCacheManagerInterface {
    /**
     * Cache assets for a given identifier.
     * Downloads assets from remote paths and stores them in an identifier-named cache directory with consistent and unique file names
     * derived from their remote paths using sha256.
     * @param identifier: Name of the directory within the root cache directory, usually an in-app message schedule ID
     * @param assets: An array of remote URL paths for the assets associated with the provided identifier
     * @return [AirshipCachedAssetsInterface]
     */
    suspend fun cacheAsset(identifier: String, assets: List<String>): AirshipCachedAssetsInterface?

    /**
     * Clears the cache directory associated with the identifier
     * @param identifier: Name of the directory within the root cache directory, usually an in-app message schedule ID
     */
    suspend fun clearCache(identifier: String)
}

/**
 * Downloads and caches asset files in filesystem using cancelable thread-safe tasks.
 */
internal class AssetCacheManager(
    context: Context,
    private val downloader: AssetDownloaderInterface = DefaultAssetDownloader(context),
    private val fileManager: AssetFileManagerInterface = DefaultAssetFileManager(context),
    private val scope: CoroutineScope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())
) : AssetCacheManagerInterface {

    private val tasks = mutableMapOf<String, Deferred<AirshipCachedAssets?>>()
    private val lock = Object()

    override suspend fun cacheAsset(
        identifier: String, assets: List<String>
    ): AirshipCachedAssetsInterface? {
        val running = synchronized(lock) { tasks[identifier] }
        if (running != null) {
            return running.await()
        }

        val task = scope.async {
            val cached = try {
                val directory = fileManager.ensureCacheDirectory(identifier)
                AirshipCachedAssets(directory, fileManager)
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to cache asset" }
                return@async null
            }

            assets.forEach {
                try {
                    val url = convertToUrl(it) ?: return@forEach
                    val tempURI = downloader.downloadAsset(url).await()
                    if (!cached.isCached(it)) {
                        yield()
                        if (!isActive) { return@async cached }

                        cached.cacheURL(it)?.let { cacheURI ->
                            fileManager.moveAsset(tempURI, cacheURI.toURI())
                            cached.generateAndStoreMetadata(it)
                        }
                    }
                } catch (ex: Exception) {
                    UALog.e(ex) { "Failed to cache asset: $it" }
                }
            }

            return@async cached
        }

        synchronized(lock) { tasks[identifier] = task }
        return task.await()
    }

    override suspend fun clearCache(identifier: String) {
        tasks.remove(identifier)?.cancel()
        try {
            fileManager.clearAssets(identifier)
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to clear cache" }
        }
    }

    private fun convertToUrl(string: String): URL? {
        return try {
            URL(string)
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to convert string $string to url" }
            null
        }
    }
}
