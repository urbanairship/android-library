/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets

import android.content.Context
import androidx.core.net.toUri
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Downloads and caches asset files in filesystem using cancelable thread-safe tasks.
 */
internal class AssetCacheManager(
    context: Context,
    private val downloader: AssetDownloader = DefaultAssetDownloader(context),
    private val fileManager: AssetFileManager = DefaultAssetFileManager(context),
    private val dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
    private val tasks = mutableMapOf<String, Deferred<Result<AirshipCachedAssets>>>()
    private val lock = ReentrantLock()

    /**
     * Cache assets for a given identifier.
     *
     * Downloads assets from remote paths and stores them in an identifier-named cache directory with consistent and unique file names
     * derived from their remote paths using sha256.
     *
     * @param identifier directory within the root cache directory, usually a schedule ID.
     * @param assets array of remote URLs for assets associated with the identifier.
     *
     * @return A [Result] of [AirshipCachedAssets].
     */
    suspend fun cacheAsset(
        identifier: String, assets: List<String>
    ): Result<AirshipCachedAssets> {
        // Await the running task, if it exists
        lock.withLock { tasks[identifier] }?.await()

        val task = scope.async {
            try {
                val directory = fileManager.ensureCacheDirectory(identifier)
                val cache = DefaultAirshipCachedAssets(directory, fileManager)

                // Using toSet() here to remove any duplicates from assets
                for (asset in assets.toSet()) {
                    if (!isActive) break
                    if (cache.isCached(asset)) break

                    cache.cacheUri(asset)?.let { cacheURI ->
                        val tempURI = downloader.downloadAsset(asset.toUri())
                        if (tempURI == null) {
                            throw IllegalStateException("Failed to download asset for $identifier! $asset")
                        } else {
                            fileManager.moveAsset(tempURI, cacheURI)
                            cache.generateAndStoreMetadata(asset)
                        }
                    }
                }

                if (isActive) {
                    Result.success(cache)
                } else {
                    Result.failure(CancellationException())
                }
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to cache assets for $identifier!" }
                Result.failure<AirshipCachedAssets>(ex)
            }
        }

        // Add our task and wait for it to complete
        lock.withLock { tasks[identifier] = task }
        return task.await()
    }

    /**
     * Clears the cache directory associated with the identifier.
     *
     * @param identifier the directory within the root cache directory, usually a schedule ID.
     */
    suspend fun clearCache(identifier: String) = withContext(dispatcher) {
        // Remove and cancel the running task, if it exists
        lock.withLock { tasks.remove(identifier) }?.cancel()

        try {
            fileManager.clearAssets(identifier)
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to clear cache" }
        }
    }
}
