package com.urbanairship.automation.rewrite.inappmessage.assets

import android.graphics.BitmapFactory
import android.os.Parcel
import android.os.Parcelable
import android.util.Size
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.UAStringUtil
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.StringWriter
import java.net.URI
import java.net.URL
import java.util.Objects

/**
 * Convenience interface representing an assets directory containing asset files
 * with filenames derived from their remote URL using sha256.
 */
public interface AirshipCachedAssetsInterface : Parcelable {

    /**
     * Return URL at which to cache a given asset
     * @param remoteURL: URL from which the cached data is fetched
     * @return [URL] at which to cache a given asset
     */
    public fun cacheURL(remoteURL: String): URL?

    /**
     * Checks if a [URL] is cached
     * @param remoteURL: URL from which the cached data is fetched
     * @return `true` if cached, otherwise `false`.
     */
    public fun isCached(remoteURL: String): Boolean

    /**
     * Returns the downloaded media size.
     * @param remoteURL: URL from which the cached data is fetched
     * @return [Size]. If the asset hasn't been cached or it fails to get either width or height
     * the missing fields are filled with `-1`
     */
    public fun getMediaSize(remoteURL: String): Size
}

internal class EmptyAirshipCachedAssets : AirshipCachedAssetsInterface {
    override fun cacheURL(remoteURL: String): URL? = null
    override fun isCached(remoteURL: String): Boolean = false
    override fun getMediaSize(remoteURL: String): Size = Size(-1, -1)

    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { }

    companion object : Parcelable.Creator<EmptyAirshipCachedAssets> {
        override fun createFromParcel(source: Parcel?): EmptyAirshipCachedAssets {
            return EmptyAirshipCachedAssets()
        }

        override fun newArray(size: Int): Array<EmptyAirshipCachedAssets?> = arrayOfNulls(size)
    }
}

internal class AirshipCachedAssets(
    rootDirectory: URI,
    private val fileManager: AssetFileManagerInterface
): AirshipCachedAssetsInterface {

    private val directory = File(rootDirectory.path)
    private val metadataCache = mutableMapOf<String, JsonValue>()

    override fun cacheURL(remoteURL: String): URL? {
        try {
            val remote = URL(remoteURL)
            val destination = getCachedAssetURI(remote)
            if (!fileManager.assetItemExists(destination)) { return null }

            return destination.toURL()
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to get cache url" }
            return null
        }
    }

    private fun metadataURI(mediaURI: URI): File {
        val path = mediaURI.path + METADATA_SUFFIX
        return File(path)
    }

    override fun isCached(remoteURL: String): Boolean {
        try {
            val remote = URL(remoteURL)
            val destination = getCachedAssetURI(remote)
            return fileManager.assetItemExists(destination)
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to get cache url" }
            return false
        }
    }

    override fun getMediaSize(remoteURL: String): Size {
        return synchronized(metadataCache) {
            val cached = metadataCache[remoteURL]
            if (cached != null) { return@synchronized jsonToSize(cached) }

            val local = cacheURL(remoteURL)?.toOptionalURI() ?: return@synchronized Size(-1, -1)

            val loaded = readJson(metadataURI(local))
            metadataCache[remoteURL] = loaded
            jsonToSize(loaded)
        }
    }

    internal fun generateAndStoreMetadata(url: String) {
        if (!directory.exists()) { return }

        val mediaURI = cacheURL(url)?.toOptionalURI() ?: return

        try {
            if (!fileManager.assetItemExists(mediaURI)) { return }
        } catch (ex: IOException) {
            UALog.e(ex) { "Failed to read file $mediaURI" }
            return
        }

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(mediaURI.path, options)

        val json = sizeToJson(Size(options.outWidth, options.outHeight))

        writeJson(metadataURI(mediaURI), json)
        synchronized(metadataCache) { metadataCache[url] =json }
    }

    private fun jsonToSize(json: JsonValue): Size {
        val map = json.optMap()
        return Size(
            map.opt(METADATA_IMAGE_WIDTH).getInt(-1),
            map.opt(METADATA_IMAGE_HEIGHT).getInt(-1),
        )
    }

    private fun sizeToJson(size: Size): JsonValue = jsonMapOf(
        METADATA_IMAGE_HEIGHT to size.height,
        METADATA_IMAGE_WIDTH to size.width
    ).toJsonValue()

    private fun writeJson(destination: File, jsonValue: JsonValue) {
        try {
            FileOutputStream(destination).use {
                it.write(jsonValue.toString().toByteArray())
            }
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to write metadata." }
        }
    }

    private fun readJson(metadata: File): JsonValue {
        if (!metadata.exists()) {
            return JsonValue.NULL
        }

        try {
            BufferedReader(FileReader(metadata)).use { reader ->
                val writer = StringWriter()
                reader.copyTo(writer)
                return JsonValue.parseString(writer.toString())
            }
        } catch (e: IOException) {
            UALog.e(e) { "Error reading file" }
        } catch (e: JsonException) {
            UALog.e(e) { "Error parsing file as JSON." }
        } catch (ex: FileNotFoundException) {
            UALog.e(ex) { "Failed to read file" }
        }

        return JsonValue.NULL
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

    companion object : Parcelable.Creator<AirshipCachedAssets> {
        private const val METADATA_SUFFIX = ".metadata"
        private const val METADATA_IMAGE_WIDTH = "width"
        private const val METADATA_IMAGE_HEIGHT = "height"

        override fun createFromParcel(parcel: Parcel): AirshipCachedAssets? {
            val metadata = try {
                JsonValue.parseString(parcel.readString()).optMap()
            } catch (ex: JsonException) {
                UALog.e(ex) { "Failed to restore asset" }
                jsonMapOf()
            }

            val path = parcel.readString() ?: return null

            val rootDirectory = try {
                URI(path)
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to restore asset from $path" }
                return null
            }

            val result = AirshipCachedAssets(
                rootDirectory = rootDirectory,
                fileManager = DefaultAssetFileManager(UAirship.getApplicationContext())
            )
            result.metadataCache.putAll(metadata.map)
            return result
        }

        override fun newArray(size: Int): Array<AirshipCachedAssets?> = arrayOfNulls(size)
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        try {
            val metadata = JsonValue.wrap(metadataCache)
            parcel.writeString(metadata.toString())
            parcel.writeString(directory.absolutePath)
        } catch (ex: JsonException) {
            UALog.e(ex) { "Failed to parcelize metadata" }
        }
    }
}

private fun URL.toOptionalURI(): URI? {
    return try {
        this.toURI()
    } catch (ex: Exception) {
        UALog.e(ex) { "Failed to convert URL $this" }
        null
    }
}
