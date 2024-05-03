package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssets
import com.urbanairship.automation.rewrite.inappmessage.content.AirshipLayout
import com.urbanairship.automation.rewrite.inappmessage.content.Banner
import com.urbanairship.automation.rewrite.inappmessage.content.Custom
import com.urbanairship.automation.rewrite.inappmessage.content.Fullscreen
import com.urbanairship.automation.rewrite.inappmessage.content.HTML
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.content.Modal
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.banner.BannerDisplayDelegate
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.fullscreen.FullscreenDisplayDelegate
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.html.HtmlDisplayDelegate
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.layout.AirshipLayoutDisplayDelegate
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.modal.ModalDisplayDelegate
import com.urbanairship.automation.rewrite.utils.NetworkMonitor
import com.urbanairship.json.JsonValue
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DisplayAdapterFactoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cachedAsset: AirshipCachedAssets = mockk(relaxed = true)
    private val activityMonitor: ActivityMonitor = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)

    private lateinit var factory: DisplayAdapterFactory

    @Before
    public fun setup() {
        factory = DisplayAdapterFactory(context, networkMonitor, activityMonitor)
    }

    @Test
    public fun testAirshipAdapter() {
        verifyAirshipAdapter<ModalDisplayDelegate>(
            content = InAppMessageDisplayContent.ModalContent(
                Modal(buttons = emptyList(), template = Modal.Template.HEADER_BODY_MEDIA),
            )
        )
        verifyAirshipAdapter<BannerDisplayDelegate>(
            content = InAppMessageDisplayContent.BannerContent(
                Banner(template = Banner.Template.MEDIA_LEFT, placement = Banner.Placement.BOTTOM)
            )
        )

        verifyAirshipAdapter<FullscreenDisplayDelegate>(
            content = InAppMessageDisplayContent.FullscreenContent(
                Fullscreen(template = Fullscreen.Template.HEADER_MEDIA_BODY)
            )
        )

        verifyAirshipAdapter<HtmlDisplayDelegate>(
            content = InAppMessageDisplayContent.HTMLContent(
                HTML(url = "https://test.url", allowFullscreenDisplay = true)
            )
        )

        val embedded = """
        {
          "layout": {
            "version": 1,
            "presentation": {
              "type": "embedded",
              "embedded_id": "home_banner",
              "default_placement": {
                "size": {
                  "width": "50%",
                  "height": "50%"
                }
              }
            },
            "view": {
              "type": "container",
              "items": []
            }
          }
        }
        """.trimIndent()

        verifyAirshipAdapter<AirshipLayoutDisplayDelegate>(
            content = InAppMessageDisplayContent.AirshipLayoutContent(
                AirshipLayout.fromJson(JsonValue.parseString(embedded))
            )
        )
    }

    @Test
    public fun testCustomAdapters() {
        verifyCustomAdapter(
            type = CustomDisplayAdapterType.MODAL,
            displayContent = InAppMessageDisplayContent.ModalContent(
                Modal(template = Modal.Template.HEADER_BODY_MEDIA, buttons = emptyList())
            )
        )

        verifyCustomAdapter(
            type = CustomDisplayAdapterType.BANNER,
            displayContent = InAppMessageDisplayContent.BannerContent(
                Banner(template = Banner.Template.MEDIA_LEFT, placement = Banner.Placement.BOTTOM)
            )
        )

        verifyCustomAdapter(
            type = CustomDisplayAdapterType.FULLSCREEN,
            displayContent = InAppMessageDisplayContent.FullscreenContent(
                Fullscreen(template = Fullscreen.Template.HEADER_BODY_MEDIA)
            )
        )

        verifyCustomAdapter(
            type = CustomDisplayAdapterType.HTML,
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML(url = "https://test.url", allowFullscreenDisplay = true)
            )
        )

        verifyCustomAdapter(
            type = CustomDisplayAdapterType.CUSTOM,
            displayContent = InAppMessageDisplayContent.CustomContent(
                Custom(JsonValue.wrap("test"))
            )
        )
    }

    @Test
    public fun testCustomError() {
        val message = InAppMessage(
            name = "layout",
            displayContent = InAppMessageDisplayContent.CustomContent(
                Custom(JsonValue.wrap("test"))
            )
        )

        assertTrue(factory.makeAdapter(message, cachedAsset).isFailure)
    }

    private fun <T : DelegatingDisplayAdapter.Delegate> verifyAirshipAdapter(content: InAppMessageDisplayContent) {
        val message = InAppMessage(name = "test", displayContent = content)

        val adapter = factory.makeAdapter(message, cachedAsset).getOrThrow()
        val unwrappedAdapter = adapter as? DelegatingDisplayAdapter
        @Suppress("UNCHECKED_CAST")
        if (unwrappedAdapter == null || (unwrappedAdapter.delegate as? T) == null) {
            fail()
        }
    }

    private fun verifyCustomAdapter(
        type: CustomDisplayAdapterType,
        displayContent: InAppMessageDisplayContent) {

        val message = InAppMessage(name = "test", displayContent = displayContent)

        val expected: CustomDisplayAdapter = mockk(relaxed = true)
        factory.setAdapterFactoryBlock(type) { _, incomingMessage, assets ->
            assertEquals(assets, cachedAsset)
            assertEquals(incomingMessage, message)
            expected
        }

        val actual = factory.makeAdapter(message, cachedAsset).getOrThrow()
        val unwrapped = actual as? CustomDisplayAdapterWrapper
        if (unwrapped == null || unwrapped.adapter != expected) {
            fail()
        }
    }
}
