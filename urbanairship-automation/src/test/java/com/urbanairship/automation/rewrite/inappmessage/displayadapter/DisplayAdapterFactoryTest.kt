package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.content.AirshipLayout
import com.urbanairship.automation.rewrite.inappmessage.content.Banner
import com.urbanairship.automation.rewrite.inappmessage.content.Custom
import com.urbanairship.automation.rewrite.inappmessage.content.Fullscreen
import com.urbanairship.automation.rewrite.inappmessage.content.HTML
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.content.Modal
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.banner.BannerAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.fullscreen.FullScreenAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.html.HtmlDisplayAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.layout.LayoutAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.modal.ModalAdapter
import com.urbanairship.json.JsonValue
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DisplayAdapterFactoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cachedAsset: AirshipCachedAssetsInterface = mockk()
    private val activityMonitor: InAppActivityMonitor = mockk()
    private lateinit var factory: DisplayAdapterFactory

    @Before
    public fun setup() {
        factory = DisplayAdapterFactory(mockk())
    }

    @Test
    public fun testAirshipAdapter() {
        verifyAirshipAdapter<ModalAdapter>(
            content = InAppMessageDisplayContent.ModalContent(
                Modal(buttons = emptyList(), template = Modal.Template.HEADER_BODY_MEDIA),
            )
        )
        verifyAirshipAdapter<BannerAdapter>(
            content = InAppMessageDisplayContent.BannerContent(
                Banner(template = Banner.Template.MEDIA_LEFT, placement = Banner.Placement.BOTTOM)
            )
        )

        verifyAirshipAdapter<FullScreenAdapter>(
            content = InAppMessageDisplayContent.FullscreenContent(
                Fullscreen(template = Fullscreen.Template.HEADER_MEDIA_BODY)
            )
        )

        verifyAirshipAdapter<HtmlDisplayAdapter>(
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

        verifyAirshipAdapter<LayoutAdapter>(
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
    public fun testCustomThrowsNoAdapter() {
        val message = InAppMessage(
            name = "layout",
            displayContent = InAppMessageDisplayContent.CustomContent(
                Custom(JsonValue.wrap("test"))
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            factory.makeAdapter(message, cachedAsset, activityMonitor)
        }
    }

    private fun <T : DisplayAdapterInterface> verifyAirshipAdapter(content: InAppMessageDisplayContent) {
        val message = InAppMessage(name = "test", displayContent = content)

        val adapter = factory.makeAdapter(message, cachedAsset, activityMonitor)
        val unwrappedAdapter = adapter as? AirshipLayoutDisplayAdapter
        @Suppress("UNCHECKED_CAST")
        if (unwrappedAdapter == null || (unwrappedAdapter.contentAdapter as? T) == null) {
            fail()
        }
    }

    private fun verifyCustomAdapter(
        type: CustomDisplayAdapterType,
        displayContent: InAppMessageDisplayContent) {

        val message = InAppMessage(name = "test", displayContent = displayContent)

        val expected: CustomDisplayAdapterInterface = mockk()
        factory.setAdapterFactoryBlock(type) { incomingMessage, assets ->
            assertEquals(assets, cachedAsset)
            assertEquals(incomingMessage, message)
            expected
        }

        val actual = factory.makeAdapter(message, cachedAsset, activityMonitor)
        val unwrapped = actual as? CustomDisplayAdapterWrapper
        if (unwrapped == null || unwrapped.adapter != expected) {
            fail()
        }
    }
}
