package com.urbanairship.android.layout.assets

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.util.toSha256
import java.io.File
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
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
    private val downloader: AssetDownloader = mockk()
    private val fileManager: AssetFileManager = mockk()

    private lateinit var manager: AssetCacheManager
    private val root = File(context.cacheDir, rootDirectory).toUri()

    @Before
    public fun setup() {
        every { fileManager.getRootDirectory() } returns root
        every { fileManager.clearAssets(any()) } just runs

        manager = AssetCacheManager(context, downloader, fileManager)
    }

    @Test
    public fun testCacheTwoAssets(): TestResult = runTest {
        val testScheduleId = "test-schedule-id"

        val scheduleCacheRoot = File(root.path, testScheduleId).toUri()

        val assetRemoteURL1 = "http://airship.com/asset1"
        val assetRemoteURL2 = "http://airship.com/asset2"

        val localAssetFile1 = File(scheduleCacheRoot.path, "3d3ee62a972ab30a4e238871429bb71cdc8268db7062ba382474e4ef2cde1a31").toUri()
        val localAssetFile2 = File(scheduleCacheRoot.path, "86b453864690e2e7faeb987d7e87e90d7d08275eddc4bfe4cd78972ab4a8fec9").toUri()

        coEvery { downloader.downloadAsset(any<Uri>()) } answers {
            val lastSegment = firstArg<Uri>().lastPathSegment ?: ""
            "file:///tmp/$lastSegment".toUri()
        }

        every { fileManager.ensureCacheDirectory(any()) } answers {
            assertEquals(testScheduleId, firstArg())
            scheduleCacheRoot.toFile()
        }

        val flags = mutableMapOf(localAssetFile1 to false, localAssetFile2 to false)

        every { fileManager.assetItemExists(any<Uri>()) } answers {
            val firstArg = firstArg<Uri>()

            val shouldExist = flags[firstArg] ?: false

            if (scheduleCacheRoot == firstArg) { return@answers true }
            if (localAssetFile1 == firstArg && shouldExist) { return@answers true }
            if (localAssetFile2 == firstArg && shouldExist) { return@answers true }
            if (shouldExist) {
                fail()
            }

            if (flags.containsKey(firstArg)) {
                flags[firstArg] = true
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

        assertEquals(cachedAsset.cacheUri(assetRemoteURL1), localAssetFile1)
        assertEquals(cachedAsset.cacheUri(assetRemoteURL2), localAssetFile2)

        verify(exactly = 1) { fileManager.moveAsset(eq("file:///tmp/asset1".toUri()), eq(localAssetFile1)) }
        verify(exactly = 1) { fileManager.moveAsset(eq("file:///tmp/asset2".toUri()), eq(localAssetFile2)) }

        assertTrue(isFirstFileMoved)
        assertTrue(isSecondFileMoved)
    }

    @Test
    public fun testClearCacheDuringActiveDownload(): TestResult = runTest {
        val testScheduleId = "test-schedule-id"
        val scheduleCacheRoot = File(root.path, testScheduleId)

        coEvery { downloader.downloadAsset(any()) } coAnswers  {
            delay(250)
            "file:///tmp/asset".toUri()
        }

        every { fileManager.ensureCacheDirectory(any()) } answers { scheduleCacheRoot }

        var shouldExist = false
        every { fileManager.assetItemExists(any()) } answers {
            val result = shouldExist
            shouldExist = !shouldExist
            result
        }

        every { fileManager.moveAsset(any(), any()) } just runs

        val remote = "http://airship.com/asset"
        launch {
            manager.cacheAsset(testScheduleId, assets = listOf(remote))
        }

        yield()
        delay(100)
        manager.clearCache(testScheduleId)

        verify(exactly = 0) { fileManager.moveAsset(any(), any()) }
        verify(exactly = 1) { fileManager.clearAssets(testScheduleId) }
    }

    @Test
    public fun testDuplicateUrlsInList_downloadsOnce(): TestResult = runTest {
        val testScheduleId = "dup-schedule"
        val scheduleCacheRoot = File(root.path, testScheduleId).toUri()
        val url = "http://airship.com/same-asset"

        coEvery { downloader.downloadAsset(any<Uri>()) } answers {
            val seg = firstArg<Uri>().lastPathSegment ?: ""
            "file:///tmp/$seg".toUri()
        }

        every { fileManager.ensureCacheDirectory(any()) } answers {
            assertEquals(testScheduleId, firstArg())
            scheduleCacheRoot.toFile()
        }

        every { fileManager.moveAsset(any(), any()) } just runs

        val hash = requireNotNull(url.toUri().path?.toSha256())
        val expectedLocal = File(scheduleCacheRoot.toFile(), hash).toUri()

        val flagsDup = mutableMapOf(expectedLocal to false)
        every { fileManager.assetItemExists(any<Uri>()) } answers {
            val arg = firstArg<Uri>()
            val shouldExist = flagsDup[arg] ?: false
            if (arg == expectedLocal && shouldExist) return@answers true
            if (shouldExist) {
                fail()
            }
            if (flagsDup.containsKey(arg)) {
                flagsDup[arg] = true
            }
            false
        }

        manager.cacheAsset(testScheduleId, listOf(url, url)).getOrThrow()

        coVerify(exactly = 1) { downloader.downloadAsset(url.toUri()) }
        verify(exactly = 1) { fileManager.moveAsset(any(), any()) }
    }

    @Test
    public fun testAlreadyCached_skipsDownload(): TestResult = runTest {
        val testScheduleId = "cached-schedule"
        val scheduleCacheRoot = File(root.path, testScheduleId).toUri()
        val url = "http://airship.com/preloaded"

        every { fileManager.ensureCacheDirectory(any()) } answers { scheduleCacheRoot.toFile() }
        every { fileManager.assetItemExists(any()) } returns true

        manager.cacheAsset(testScheduleId, listOf(url)).getOrThrow()

        coVerify(exactly = 0) { downloader.downloadAsset(any()) }
        verify(exactly = 0) { fileManager.moveAsset(any(), any()) }
    }

    @Test
    public fun testDownloadReturnsNull_throwsIllegalStateException(): TestResult = runTest {
        val testScheduleId = "fail-schedule"
        val scheduleCacheRoot = File(root.path, testScheduleId).toUri()
        val url = "http://airship.com/missing"

        every { fileManager.ensureCacheDirectory(any()) } answers { scheduleCacheRoot.toFile() }
        every { fileManager.assetItemExists(any()) } returns false
        coEvery { downloader.downloadAsset(any()) } returns null

        try {
            manager.cacheAsset(testScheduleId, listOf(url))
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Failed to download"))
        }
    }

    @Test
    public fun testEmptyAssetList_succeedsWithoutDownload(): TestResult = runTest {
        val testScheduleId = "empty-schedule"
        every { fileManager.ensureCacheDirectory(testScheduleId) } answers {
            File(root.path, testScheduleId)
        }

        manager.cacheAsset(testScheduleId, emptyList()).getOrThrow()

        coVerify(exactly = 0) { downloader.downloadAsset(any()) }
        verify(exactly = 1) { fileManager.ensureCacheDirectory(testScheduleId) }
    }

    @Test
    public fun testClearCache_invokesClearAssets(): TestResult = runTest {
        val id = "clear-me"
        manager.clearCache(id)
        verify(exactly = 1) { fileManager.clearAssets(id) }
    }
}
