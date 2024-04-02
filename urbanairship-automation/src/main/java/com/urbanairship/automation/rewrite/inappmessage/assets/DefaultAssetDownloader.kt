package com.urbanairship.automation.rewrite.inappmessage.assets

import android.content.Context
import com.urbanairship.AirshipDispatchers
import com.urbanairship.util.FileUtils
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import kotlin.jvm.Throws
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

internal class DefaultAssetDownloader(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())
): AssetDownloaderInterface {

    private val cacheFolder: File = context.cacheDir

    @Throws(IOException::class)
    override fun downloadAsset(remoteURL: URL): Deferred<URI> {
        return scope.async {
            val tmpCacheFile = File(cacheFolder, lastPathComponent(remoteURL))
            FileUtils.downloadFile(remoteURL, tmpCacheFile)
            return@async tmpCacheFile.toURI()
        }
    }

    private fun lastPathComponent(url: URL): String {
        val index = url.path.lastIndexOf("/")
        if (index < 0) { return url.path }
        return url.path.substring(index)
    }
}
