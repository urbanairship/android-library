package com.urbanairship.iam.assets

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.util.FileUtils
import com.urbanairship.util.toURL
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DefaultAssetDownloaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val downloader = DefaultAssetDownloader(context)
    private val testUri = "https://airship.com/whatever".toUri()

    @Test
    public fun testDownloadAssetDataMatches(): TestResult = runTest {
        mockkStatic(FileUtils::class)
        every { FileUtils.downloadFile(any(), any()) } returns mockk()

        downloader.downloadAsset(testUri)

        verify { FileUtils.downloadFile(eq(testUri.toURL()), any()) }
    }
}
