package com.urbanairship.iam.adapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.HTML
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.getUrlInfos
import com.urbanairship.iam.info.InAppMessageMediaInfo
import com.urbanairship.automation.utils.NetworkMonitor
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DelegatingDisplayAdapterTest
{
    private val asset: AirshipCachedAssets = mockk()

    private val isNetworkConnected = MutableStateFlow(false)
    private val networkMonitor: NetworkMonitor = mockk() {
        every { isConnected } returns isNetworkConnected
    }

    private val delegate: DelegatingDisplayAdapter.Delegate = mockk(relaxed = true) {
        every { activityPredicate } returns null
    }
    private val activityMonitor = TestActivityMonitor()


    @Test
    public fun testIsReadyActivity(): TestResult = runTest {
        val message = InAppMessage(
            name = "no assets",
            displayContent = InAppMessageDisplayContent.BannerContent(
                Banner(placement = Banner.Placement.BOTTOM, template = Banner.Template.MEDIA_LEFT)
            )
        )
        assertTrue(message.getUrlInfos().isEmpty())

        makeAdapter(message).isReady.test {
            assertFalse(awaitItem())
            activityMonitor.resumeActivity(mockk())
            assertTrue(awaitItem())
        }
    }

    @Test
    public fun testIsReadyNoAssets(): TestResult = runTest {
        activityMonitor.resumeActivity(mockk())

        val message = InAppMessage(
            name = "no assets",
            displayContent = InAppMessageDisplayContent.BannerContent(
                Banner(placement = Banner.Placement.BOTTOM, template = Banner.Template.MEDIA_LEFT)
            )
        )
        assertTrue(message.getUrlInfos().isEmpty())

        makeAdapter(message).isReady.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    public fun testIsReadyImageAssetCached(): TestResult = runTest {
        activityMonitor.resumeActivity(mockk())

        val message = InAppMessage(
            name = "image assets",
            displayContent = InAppMessageDisplayContent.BannerContent(
                Banner(
                    media = InAppMessageMediaInfo(
                        url = "https://image.url",
                        type = InAppMessageMediaInfo.MediaType.IMAGE,
                        description = null),
                    placement = Banner.Placement.BOTTOM,
                    template = Banner.Template.MEDIA_LEFT)
            )
        )

        every { asset.isCached("https://image.url") } returns true

        val adapter = makeAdapter(message)
        assertTrue(adapter.isReady.value)
        adapter.isReady.test {
            assertTrue(awaitItem())
        }

        verify { asset.isCached("https://image.url") }
    }

    @Test
    public fun testIsReadyImageAssetNotCached(): TestResult = runTest {
        activityMonitor.resumeActivity(mockk())

        val message = InAppMessage(
            name = "image assets",
            displayContent = InAppMessageDisplayContent.BannerContent(
                Banner(
                    media = InAppMessageMediaInfo(
                        url = "https://image.url",
                        type = InAppMessageMediaInfo.MediaType.IMAGE,
                        description = null),
                    placement = Banner.Placement.BOTTOM,
                    template = Banner.Template.MEDIA_LEFT)
            )
        )

        every { asset.isCached("https://image.url") } returns false

        makeAdapter(message).isReady.test {
            assertFalse(awaitItem())
            isNetworkConnected.value = true
            assertTrue(awaitItem())
        }

        verify { asset.isCached("https://image.url") }
    }

    @Test
    public fun testIsReadyVideoAsset(): TestResult = runTest {
        activityMonitor.resumeActivity(mockk())

        val message = InAppMessage(
            name = "video assets",
            displayContent = InAppMessageDisplayContent.BannerContent(
                Banner(
                    media = InAppMessageMediaInfo(
                        url = "https://video.url",
                        type = InAppMessageMediaInfo.MediaType.VIDEO,
                        description = null),
                    placement = Banner.Placement.BOTTOM,
                    template = Banner.Template.MEDIA_LEFT)
            )
        )

        makeAdapter(message).isReady.test {
            assertFalse(awaitItem())
            isNetworkConnected.value = true
            assertTrue(awaitItem())
        }
    }

    @Test
    public fun testIsReadyHTMLAsset(): TestResult = runTest {
        activityMonitor.resumeActivity(mockk())

        val message = InAppMessage(
            name = "video assets",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML("https://html.url", allowFullscreenDisplay = true)
            )
        )

        makeAdapter(message).isReady.test {
            assertFalse(awaitItem())
            isNetworkConnected.value = true
            assertTrue(awaitItem())
        }
    }

    @Test
    public fun testIsReadyHTMLAssetDoesNotRequireConnectivity(): TestResult = runTest {
        activityMonitor.resumeActivity(mockk())

        val message = InAppMessage(
            name = "video assets",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML("https://html.url", allowFullscreenDisplay = true, requiresConnectivity = false)
            )
        )

        makeAdapter(message).isReady.test {
            assertTrue(awaitItem())
        }
    }


    private fun makeAdapter(message: InAppMessage): DelegatingDisplayAdapter {
        return DelegatingDisplayAdapter(
            message = message,
            assets = asset,
            delegate = delegate,
            networkMonitor = networkMonitor,
            activityMonitor = activityMonitor
        )
    }
}
