package com.urbanairship.automation.rewrite.inappmessage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.assets.AssetCacheManagerInterface
import com.urbanairship.automation.rewrite.inappmessage.content.Banner
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterFactoryInterface
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterInterface
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinatorInterface
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinatorManagerInterface
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageMediaInfo
import com.urbanairship.json.JsonValue
import java.util.UUID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppMessageAutomationPreparerTest {
    private val activityMonitor = TestActivityMonitor()
    private val stateTracker = InAppActivityMonitor(activityMonitor)
    private val assetsManager: AssetCacheManagerInterface = mockk()
    private val adapterFactory: DisplayAdapterFactoryInterface = mockk()
    private val coordinatorManager: DisplayCoordinatorManagerInterface = mockk()

    private val message = InAppMessage(
        name = "test",
        displayContent = InAppMessageDisplayContent.BannerContent(
            Banner(
                media = InAppMessageMediaInfo("https://banner.url", InAppMessageMediaInfo.MediaType.IMAGE, null),
                placement = Banner.Placement.BOTTOM,
                template = Banner.Template.MEDIA_LEFT
            ))
    )
    private val preparedScheduleInfo = PreparedScheduleInfo(
        scheduleID = UUID.randomUUID().toString(),
        campaigns = JsonValue.wrap("campaigns"),
        contactID = UUID.randomUUID().toString()
    )

    private val preparer = InAppMessageAutomationPreparer(stateTracker, assetsManager, coordinatorManager, adapterFactory)


    @Test
    public fun testPrepare(): TestResult = runTest {
        val cachedAsset: AirshipCachedAssetsInterface = mockk()
        coEvery { assetsManager.cacheAsset(any(), any()) } answers {
            assertEquals(preparedScheduleInfo.scheduleID, firstArg())
            assertEquals(listOf("https://banner.url"), secondArg())
            cachedAsset
        }

        val coordinator: DisplayCoordinatorInterface = mockk()
        every { coordinatorManager.displayCoordinator(any()) } answers {
            assertEquals(message, firstArg())
            coordinator
        }

        val adapter: DisplayAdapterInterface = mockk()
        every { adapterFactory.makeAdapter(any(), any(), any()) } answers {
            assertEquals(message, firstArg())
            assertEquals(cachedAsset, secondArg())
            adapter
        }

        val result = preparer.prepare(message, preparedScheduleInfo)
        assertEquals(message, result.message)
        assertEquals(coordinator, result.displayCoordinator)
        assertEquals(adapter, result.displayAdapter)
    }

    @Test
    public fun testPrepareFailedAssets(): TestResult = runTest {
        val coordinator: DisplayCoordinatorInterface = mockk()
        every { coordinatorManager.displayCoordinator(any()) } returns coordinator

        val adapter: DisplayAdapterInterface = mockk()
        every { adapterFactory.makeAdapter(any(), any(), any()) } returns adapter

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
        val asset: AirshipCachedAssetsInterface = mockk()
        coEvery { assetsManager.cacheAsset(any(), any()) } returns asset

        val coordinator: DisplayCoordinatorInterface = mockk()
        every { coordinatorManager.displayCoordinator(any()) } returns coordinator

        val adapter: DisplayAdapterInterface = mockk()
        every { adapterFactory.makeAdapter(any(), any(), any()) } answers {
            throw IllegalArgumentException()
        }

        try {
            preparer.prepare(message, preparedScheduleInfo)
            fail()
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    public fun testCancelled(): TestResult = runTest {
        val scheduleID = UUID.randomUUID().toString()
        coEvery { assetsManager.clearCache(any()) } answers { }

        preparer.cancelled(scheduleID)
        coVerify { assetsManager.clearCache(eq(scheduleID)) }
    }
}
