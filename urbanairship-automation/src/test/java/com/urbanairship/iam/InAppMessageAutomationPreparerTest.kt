package com.urbanairship.iam

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.iam.adapter.DisplayAdapter
import com.urbanairship.iam.adapter.DisplayAdapterFactory
import com.urbanairship.iam.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.assets.AssetCacheManager
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.coordinator.DisplayCoordinator
import com.urbanairship.iam.coordinator.DisplayCoordinatorManager
import com.urbanairship.iam.info.InAppMessageMediaInfo
import com.urbanairship.json.JsonValue
import java.util.UUID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppMessageAutomationPreparerTest {
    private val assetsManager: AssetCacheManager = mockk()
    private val adapterFactory: DisplayAdapterFactory = mockk()
    private val coordinatorManager: DisplayCoordinatorManager = mockk()
    private val analyticsFactory: InAppMessageAnalyticsFactory = mockk(relaxed = true)

    private val message = InAppMessage(
        name = "test",
        displayContent = InAppMessageDisplayContent.BannerContent(
            Banner(
                media = InAppMessageMediaInfo("https://banner.url", InAppMessageMediaInfo.MediaType.IMAGE, null),
                placement = Banner.Placement.BOTTOM,
                template = Banner.Template.MEDIA_LEFT
            )
        )
    )
    private val preparedScheduleInfo = PreparedScheduleInfo(
        scheduleId = UUID.randomUUID().toString(),
        campaigns = JsonValue.wrap("campaigns"),
        contactId = UUID.randomUUID().toString(),
        triggerSessionId = UUID.randomUUID().toString(),
        additionalAudienceCheckResult = true
    )

    private val preparer = InAppMessageAutomationPreparer(assetsManager, coordinatorManager, adapterFactory, analyticsFactory)


    @Test
    public fun testPrepare(): TestResult = runTest {
        val cachedAsset: AirshipCachedAssets = mockk()
        coEvery { assetsManager.cacheAsset(any(), any()) } answers {
            assertEquals(preparedScheduleInfo.scheduleId, firstArg())
            assertEquals(listOf("https://banner.url"), secondArg())
            Result.success(cachedAsset)
        }

        val coordinator: DisplayCoordinator = mockk()
        every { coordinatorManager.displayCoordinator(any()) } answers {
            assertEquals(message, firstArg())
            coordinator
        }

        val adapter: DisplayAdapter = mockk()
        every { adapterFactory.makeAdapter(any(), any()) } answers {
            assertEquals(message, firstArg())
            assertEquals(cachedAsset, secondArg())
            Result.success(adapter)
        }

        val result = preparer.prepare(message, preparedScheduleInfo).getOrThrow()
        assertEquals(message, result.message)
        assertEquals(coordinator, result.displayCoordinator)
        assertEquals(adapter, result.displayAdapter)
    }

    @Test
    public fun testPrepareFailedAssets(): TestResult = runTest {
        val coordinator: DisplayCoordinator = mockk()
        every { coordinatorManager.displayCoordinator(any()) } returns coordinator

        val adapter: DisplayAdapter = mockk()
        every { adapterFactory.makeAdapter(any(), any()) } returns Result.success(adapter)

        coEvery { assetsManager.cacheAsset(any(), any()) } answers  {
            throw IllegalArgumentException()
        }

        try {
            preparer.prepare(message, preparedScheduleInfo)
            fail()
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    public fun testPrepareFailedAdapter(): TestResult = runTest {
        val asset: AirshipCachedAssets = mockk()
        coEvery { assetsManager.cacheAsset(any(), any()) } returns Result.success(asset)

        val coordinator: DisplayCoordinator = mockk()
        every { coordinatorManager.displayCoordinator(any()) } returns coordinator

        every { adapterFactory.makeAdapter(any(), any()) } returns Result.failure(IllegalArgumentException("failed"))

        assertTrue(preparer.prepare(message, preparedScheduleInfo).isFailure)
    }

    @Test
    public fun testCancelled(): TestResult = runTest {
        val scheduleID = UUID.randomUUID().toString()
        coEvery { assetsManager.clearCache(any()) } answers { }

        preparer.cancelled(scheduleID)
        coVerify { assetsManager.clearCache(eq(scheduleID)) }
    }
}
