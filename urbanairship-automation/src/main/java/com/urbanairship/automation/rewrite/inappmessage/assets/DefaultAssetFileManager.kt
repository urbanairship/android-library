package com.urbanairship.automation.rewrite.inappmessage.assets

import android.content.Context
import android.os.storage.StorageManager
import com.urbanairship.util.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import kotlin.jvm.Throws

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

        storageManager.setCacheBehaviorGroup(file, true)
        return file.toURI()
    }

    @Throws(IOException::class)
    override fun assetItemExists(cacheURI: URI): Boolean {
        return File(cacheURI.path).exists()
    }

    @Throws(IOException::class)
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

    @Throws(IOException::class)
    override fun clearAssets(identifier: String) {
        FileUtils.deleteRecursively(File(rootFolder, identifier))
    }

    @Throws(IOException::class)
    private fun ensureRootCacheDirectory(): URI {
        if (!rootFolder.exists()) {
            if (!rootFolder.mkdirs()) {
                throw IOException("Failed to create cache folder")
            }
        }

        return rootFolder.toURI()
    }

    private fun copy(from: File, to: File) {
        val source = FileInputStream(from)
        val destination = FileOutputStream(to)

        val buffer = ByteArray(8192)
        source.use { input ->
            destination.use { fileOut ->

                while (true) {
                    val length = input.read(buffer)
                    if (length <= 0)
                        break
                    fileOut.write(buffer, 0, length)
                }
                fileOut.flush()
            }
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "com.urbanairship.iam.assets"
    }
}
