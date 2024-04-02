package com.urbanairship.automation.rewrite.inappmessage.assets

import android.content.Context
import com.urbanairship.util.UAStringUtil
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import java.util.Objects
import kotlin.jvm.Throws

/**
 * Convenience interface representing an assets directory containing asset files
 * with filenames derived from their remote URL using sha256.
 */
public interface AirshipCachedAssetsInterface {

    /**
     * Return URL at which to cache a given asset
     * @param remoteURL: URL from which the cached data is fetched
     * @return [URL] at which to cache a given asset
     */
    public fun cacheURL(remote: URL): URL?

    /**
     * Checks if a [URL] is cached
     * @param remoteURL: URL from which the cached data is fetched
     * @return `true` if cached, otherwise `false`.
     */
    public fun isCached(remote: URL): Boolean
}

internal class EmptyAirshipCachedAssets : AirshipCachedAssetsInterface {
    override fun cacheURL(remote: URL): URL? = null
    override fun isCached(remote: URL): Boolean = false
}

internal class AirshipCachedAssets(
    rootDirectory: URI,
    private val fileManager: AssetFileManagerInterface
): AirshipCachedAssetsInterface {

    private val directory = File(rootDirectory.path)

    override fun cacheURL(remote: URL): URL? {
        val destination = getCachedAssetURI(remote)
        if (!fileManager.assetItemExists(destination)) { return null }

        return destination.toURL()
    }

    override fun isCached(remote: URL): Boolean {
        val destination = getCachedAssetURI(remote)
        return fileManager.assetItemExists(destination)
    }

    @Throws(IOException::class)
    private fun getCachedAssetURI(remote: URL): URI {
        val hash = UAStringUtil.sha256(remote.path) ?: throw IOException("Failed to generate hash")
        return File(directory, hash).toURI()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipCachedAssets

        return directory == other.directory
    }

    override fun hashCode(): Int {
        return Objects.hash(directory)
    }
}
