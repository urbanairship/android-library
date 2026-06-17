/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.assets

import android.content.Context
import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.core.net.toUri
import com.urbanairship.UALog
import com.urbanairship.util.FileUtils
import com.urbanairship.util.toURL
import java.io.File
import java.net.MalformedURLException
import java.util.UUID

/**
 * Wrapper for the download tasks that is responsible for downloading assets
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AssetDownloader {

    /** Downloads the asset from a remote URL and returns its temporary local URL. */
    public suspend fun downloadAsset(remoteUri: Uri): Uri?
}

internal class DefaultAssetDownloader(context: Context): AssetDownloader {
    private val cacheFolder: File = context.cacheDir

    @Throws(MalformedURLException::class)
    override suspend fun downloadAsset(remoteUri: Uri): Uri? {
        return remoteUri.lastPathSegment?.let { lastPathSegment ->
            val uuid = UUID.randomUUID().toString()
            val tmpFile = File(cacheFolder, "${uuid}-$lastPathSegment")
            val result = FileUtils.downloadFile(remoteUri.toURL(), tmpFile)
            if (result.isSuccess) {
                UALog.d { "AssetDownloader: download succeeded url=$remoteUri statusCode=${result.statusCode} tmpFile=$tmpFile (exists=${tmpFile.exists()}, size=${tmpFile.length()}b)" }
                tmpFile.toUri()
            } else {
                UALog.w { "AssetDownloader: download failed url=$remoteUri statusCode=${result.statusCode} tmpFileExists=${tmpFile.exists()}" }
                null
            }
        }
    }
}
