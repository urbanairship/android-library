package com.urbanairship.automation.rewrite.inappmessage.assets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.net.URI
import java.net.URL
import kotlin.time.Duration
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AssetCacheManagerTest {
    private val rootDirectory = "com.urbanairship.iam.assets"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val downloader: AssetDownloaderInterface = mockk()
    private val fileManager: AssetFileManagerInterface = mockk()

    private lateinit var manager: AssetCacheManager
    private val root = File(context.cacheDir, rootDirectory).toURI()

    @Before
    public fun setup() {
        every { fileManager.getRootDirectory() } returns root
        every { fileManager.clearAssets(any()) } just runs

        manager = AssetCacheManager(context, downloader, fileManager)
    }

    @Test
    public fun testCacheTwoAssets(): TestResult = runTest {
        val testScheduleId = "test-schedule-id"

        val scheduleCacheRoot = File(root.path, testScheduleId).toURI()

        val assetRemoteURL1 = "http://airship.com/asset1"
        val assetRemoteURL2 = "http://airship.com/asset2"

        val localAssetFile1 = File(scheduleCacheRoot.path, "3d3ee62a972ab30a4e238871429bb71cdc8268db7062ba382474e4ef2cde1a31").toURI()
        val localAssetFile2 = File(scheduleCacheRoot.path, "86b453864690e2e7faeb987d7e87e90d7d08275eddc4bfe4cd78972ab4a8fec9").toURI()

        coEvery { downloader.downloadAsset(any()) } answers  {
            CompletableDeferred(URI("file:///tmp/asset"))
        }

        every { fileManager.ensureCacheDirectory(any()) } answers {
            assertEquals(testScheduleId, firstArg())
            scheduleCacheRoot
        }
        val flags = mutableMapOf(localAssetFile1 to false, localAssetFile2 to false)
        every { fileManager.assetItemExists(any()) } answers {
            val shouldExist = flags[firstArg()] ?: false

            if (scheduleCacheRoot == firstArg()) { return@answers true }
            if (localAssetFile1 == firstArg() && shouldExist) { return@answers true }
            if (localAssetFile2 == firstArg() && shouldExist) { return@answers true }
            if (shouldExist) {
                fail()
            }

            if (flags.containsKey(firstArg())) {
                flags[firstArg()] = true
            }

            false
        }

        var isFirstFileMoved = false
        var isSecondFileMoved = false

        every { fileManager.moveAsset(any(), any()) } answers {
            if (localAssetFile1 == secondArg()) {
                isFirstFileMoved = true
            }
            if (localAssetFile2 == secondArg()) {
                isSecondFileMoved = true
            }
        }

        manager.clearCache(testScheduleId)

        val cachedAsset = manager.cacheAsset(testScheduleId, listOf(assetRemoteURL1,
            assetRemoteURL2
        )).getOrThrow()

        assertTrue(cachedAsset.isCached(assetRemoteURL1))
        assertTrue(cachedAsset.isCached(assetRemoteURL2))

        assertEquals(cachedAsset.cacheURL(assetRemoteURL1), localAssetFile1.toURL())
        assertEquals(cachedAsset.cacheURL(assetRemoteURL2), localAssetFile2.toURL())

        verify(exactly = 1) { fileManager.moveAsset(eq(URI("file:///tmp/asset")), eq(localAssetFile1)) }
        verify(exactly = 1) { fileManager.moveAsset(eq(URI("file:///tmp/asset")), eq(localAssetFile2)) }

        assertTrue(isFirstFileMoved)
        assertTrue(isSecondFileMoved)
    }

    @Test
    public fun testClearCacheDuringActiveDownload(): TestResult = runTest(timeout = Duration.INFINITE) {
        val testScheduleId = "test-schedule-id"
        val scheduleCacheRoot = File(root.path, testScheduleId).toURI()

        coEvery { downloader.downloadAsset(any()) } coAnswers  {
            delay(500)
            CompletableDeferred(URI("file:///tmp/asset"))
        }

        every { fileManager.ensureCacheDirectory(any()) } answers {
            scheduleCacheRoot
        }

        var shouldExist = false
        every { fileManager.assetItemExists(any()) } answers {
            val result = shouldExist
            shouldExist = !shouldExist
            result
        }

        every { fileManager.moveAsset(any(), any()) } just runs

        val remote = URL("http://airship.com/asset")
        launch {
            manager.cacheAsset(testScheduleId, assets = listOf(remote.toString()))
        }

        yield()
        delay(100)
        manager.clearCache(testScheduleId)

        verify(exactly = 0) { fileManager.moveAsset(any(), any()) }

    }
}
