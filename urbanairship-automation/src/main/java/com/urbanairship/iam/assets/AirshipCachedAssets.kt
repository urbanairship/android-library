/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.util.Size
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.UAStringUtil
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.StringWriter
import java.util.Objects
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Convenience interface representing an assets directory containing asset files
 * with filenames derived from their remote URL using sha256.
 */
public interface AirshipCachedAssets : Parcelable {

    /**
     * Return a uri at which to cache a given asset
     * @param remoteUrl: URL from which the cached data is fetched
     * @return [Uri] at which to cache a given asset
     */
    public fun cacheUri(remoteUrl: String): Uri?

    /**
     * Checks if an asset is cached
     * @param remoteUrl: url string from which the cached data is fetched
     * @return `true` if cached, otherwise `false`.
     */
    public fun isCached(remoteUrl: String): Boolean

    /**
     * Returns the downloaded media size.
     * @param remoteUrl: url string from which the cached data is fetched
     * @return [Size]. If the asset hasn't been cached or it fails to get either width or height
     * the missing fields are filled with `-1`
     */
    public fun getMediaSize(remoteUrl: String): Size
}

internal class EmptyAirshipCachedAssets : AirshipCachedAssets {
    override fun cacheUri(remoteUrl: String): Uri? = null
    override fun isCached(remoteUrl: String): Boolean = false
    override fun getMediaSize(remoteUrl: String): Size = Size(-1, -1)

    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) { }

    companion object CREATOR: Parcelable.Creator<EmptyAirshipCachedAssets> {
        override fun createFromParcel(source: Parcel?): EmptyAirshipCachedAssets {
            return EmptyAirshipCachedAssets()
        }

        override fun newArray(size: Int): Array<EmptyAirshipCachedAssets?> = arrayOfNulls(size)
    }
}

internal class DefaultAirshipCachedAssets(
    private val directory: File,
    private val fileManager: AssetFileManager
): AirshipCachedAssets {

    private val lock = ReentrantLock()
    private val metadataCache = mutableMapOf<String, JsonValue>()

    override fun cacheUri(remoteUrl: String): Uri? =
        try {
            getCachedAssetUrl(remote = remoteUrl.toUri())
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to get cached asset url! $remoteUrl" }
            null
        }

    private fun metadataUri(mediaUri: Uri): File =
        File("${mediaUri.path}.${METADATA_EXTENSION}")

    override fun isCached(remoteUrl: String): Boolean =
        try {
            val remote = remoteUrl.toUri()
            val destination = getCachedAssetUrl(remote)
            fileManager.assetItemExists(destination)
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to determine if asset is cached! $remoteUrl" }
            false
        }

    override fun getMediaSize(remoteUrl: String): Size {
        return lock.withLock {
            val cached = metadataCache[remoteUrl]
            if (cached != null) {
                jsonToSize(cached)
            } else {
                val local = cacheUri(remoteUrl) ?: return Size(-1, -1)
                val loaded = readJson(metadataUri(local))
                metadataCache[remoteUrl] = loaded
                jsonToSize(loaded)
            }
        }
    }

    internal fun generateAndStoreMetadata(url: String) {
        if (!directory.exists()) { return }

        val mediaUrl = cacheUri(url) ?: return

        try {
            if (!fileManager.assetItemExists(mediaUrl)) { return }
        } catch (ex: IOException) {
            UALog.e(ex) { "Failed to generate and store cached asset metadata! $url" }
            return
        }

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(mediaUrl.path, options)

        val json = try {
            sizeToJson(Size(options.outWidth, options.outHeight))
        } catch (ex: JsonException) {
            UALog.e(ex) { "Failed to generate cached asset metadata. Unable to convert size to json!" }
            return
        }

        writeJson(metadataUri(mediaUrl), json)
        lock.withLock { metadataCache[url] =json }
    }

    private fun jsonToSize(json: JsonValue): Size {
        val map = json.optMap()
        return Size(
            map.opt(METADATA_IMAGE_WIDTH).getInt(-1),
            map.opt(METADATA_IMAGE_HEIGHT).getInt(-1),
        )
    }

    @Throws(JsonException::class)
    private fun sizeToJson(size: Size): JsonValue = jsonMapOf(
        METADATA_IMAGE_HEIGHT to size.height,
        METADATA_IMAGE_WIDTH to size.width
    ).toJsonValue()

    private fun writeJson(destination: File, jsonValue: JsonValue) {
        try {
            destination.bufferedWriter().use {
                it.write(jsonValue.toString())
            }
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to write cached asset metadata!" }
        }
    }

    private fun readJson(metadata: File): JsonValue {
        if (!metadata.exists()) {
            return JsonValue.NULL
        }

        try {
            val writer = StringWriter()
            metadata.bufferedReader().use { reader ->
                reader.copyTo(writer)
            }
            return JsonValue.parseString(writer.toString())
        } catch (e: IOException) {
            UALog.e(e) { "Failed to read cached asset metadata!" }
        } catch (e: JsonException) {
            UALog.e(e) { "Failed to parse cached asset metadata!" }
        } catch (ex: FileNotFoundException) {
            UALog.e(ex) { "Failed to read cached asset metadata. File not found!" }
        }

        return JsonValue.NULL
    }

    @Throws(IOException::class)
    private fun getCachedAssetUrl(remote: Uri): Uri {
        val hash = UAStringUtil.sha256(remote.path)
            ?: throw IOException("Failed to generate cached asset URL hash!")
        return File(directory, hash).toUri()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultAirshipCachedAssets

        return directory == other.directory
    }

    override fun hashCode(): Int = Objects.hash(directory)

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        try {
            val metadata = JsonValue.wrap(metadataCache)
            parcel.writeString(metadata.toString())
            parcel.writeString(directory.absolutePath)
        } catch (ex: JsonException) {
            UALog.e(ex) { "Failed to write cached asset metadata to parcel!" }
        }
    }

    companion object CREATOR: Parcelable.Creator<AirshipCachedAssets> {
        @VisibleForTesting
        internal const val METADATA_EXTENSION = ".metadata"
        private const val METADATA_IMAGE_WIDTH = "width"
        private const val METADATA_IMAGE_HEIGHT = "height"

        override fun createFromParcel(parcel: Parcel): AirshipCachedAssets? {
            val metadata = try {
                JsonValue.parseString(parcel.readString()).optMap()
            } catch (ex: JsonException) {
                UALog.e(ex) { "Failed to restore cached asset metadata from parcel!" }
                jsonMapOf()
            }

            val path = parcel.readString() ?: return null
            val file = File(path)

            val directory = try {
                if (file.exists()) {
                    file
                } else {
                    UALog.e { "Failed to restore cached asset! Directory does not exist! $path" }
                    return null
                }
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to restore cached asset! $path" }
                return null
            }

            val result = DefaultAirshipCachedAssets(
                directory = directory,
                fileManager = DefaultAssetFileManager(UAirship.applicationContext)
            )
            result.metadataCache.putAll(metadata.map)

            return result
        }

        override fun newArray(size: Int): Array<AirshipCachedAssets?> = arrayOfNulls(size)
    }
}
