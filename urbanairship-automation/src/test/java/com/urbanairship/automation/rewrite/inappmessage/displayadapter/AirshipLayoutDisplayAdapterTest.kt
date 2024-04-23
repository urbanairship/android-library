package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.content.Banner
import com.urbanairship.automation.rewrite.inappmessage.content.HTML
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.getUrlInfos
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageMediaInfo
import com.urbanairship.automation.rewrite.utils.NetworkMonitor
import com.urbanairship.util.Network
import java.net.URL
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AirshipLayoutDisplayAdapterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val asset: AirshipCachedAssetsInterface = mockk()
    private val network: Network = mockk()
    private lateinit var networkMonitor: NetworkMonitor
    private val inAppActivityMonitor: InAppActivityMonitor = mockk()

    @Before
    public fun setup() {

        every { network.isConnected(any()) } answers { false }

        val monitor = NetworkMonitor(context, network)
        networkMonitor = spyk(monitor)

        every { inAppActivityMonitor.getResumedActivities(any()) } returns listOf(Activity())
        every { inAppActivityMonitor.resumedActivities } returns listOf(Activity())
    }

    @Test
    public fun testIsReadyNoAssets() {
        val message = InAppMessage(
            name = "no assets",
            displayContent = InAppMessageDisplayContent.BannerContent(
                Banner(placement = Banner.Placement.BOTTOM, template = Banner.Template.MEDIA_LEFT)
            )
        )

        assertTrue(message.getUrlInfos().isEmpty())

        val adapter = makeAdapter(message)
        assertTrue(adapter.getIsReady())
    }

    @Test
    public fun testIsReadyImageAsset() {
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

        val adapter = makeAdapter(message)
        var isAssetCached = false

        every { asset.isCached(any()) } answers {
            assertEquals("https://image.url", firstArg())
            isAssetCached
        }

        assertFalse(adapter.getIsReady())

        isAssetCached = true
        assertTrue(adapter.getIsReady())

        isAssetCached = false

        networkMonitor.notifyStateUpdate(true)

        assertTrue(adapter.getIsReady())
    }

    @Test
    public fun testIsReadyVideoAsset() {
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

        val adapter = makeAdapter(message)

        assertFalse(adapter.getIsReady())

        networkMonitor.notifyStateUpdate(true)
        assertTrue(adapter.getIsReady())
    }

    @Test
    public fun testIsReadyHTMLAsset() {
        val message = InAppMessage(
            name = "video assets",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML("https://html.url", allowFullscreenDisplay = true)
            )
        )

        val adapter = makeAdapter(message)

        assertFalse(adapter.getIsReady())
        networkMonitor.notifyStateUpdate(true)
        assertTrue(adapter.getIsReady())
    }

    @Test
    public fun testWaitForReadyNetwork(): TestResult = runTest {
        val message = InAppMessage(
            name = "video assets",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML("https://html.url", allowFullscreenDisplay = true)
            )
        )

        val adapter = makeAdapter(message)

        assertFalse(adapter.getIsReady())

        val job = backgroundScope.launch { adapter.waitForReady() }
        yield()
        networkMonitor.notifyStateUpdate(true)
        job.join()
        assertTrue(adapter.getIsReady())
    }

    private fun makeAdapter(message: InAppMessage): AirshipLayoutDisplayAdapter {
        return AirshipLayoutDisplayAdapter(
            message = message,
            assets = asset,
            network = networkMonitor,
            activityMonitor = inAppActivityMonitor
        )
    }
}
