package com.urbanairship.iam.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.Fullscreen
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.content.Modal
import com.urbanairship.json.JsonValue
import kotlin.time.Duration.Companion.minutes
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DefaultInAppDisplayImpressionRuleProviderTest {
    private val provider = DefaultInAppDisplayImpressionRuleProvider()

    @Test
    public fun testCustomMessage() {
        val rule = provider.impressionRules(
            InAppMessage(
                name = "woot",
                displayContent = InAppMessageDisplayContent.CustomContent(
                    Custom(JsonValue.wrap("neat"))
                )
            )
        )
        assertEquals(InAppDisplayImpressionRule.Once, rule)
    }

    @Test
    public fun testFullscreenMessage() {
        val rule = provider.impressionRules(
            InAppMessage(
                name = "woot",
                displayContent = InAppMessageDisplayContent.FullscreenContent(
                    Fullscreen(template = Fullscreen.Template.HEADER_BODY_MEDIA)
                )
            )
        )
        assertEquals(InAppDisplayImpressionRule.Once, rule)
    }

    @Test
    public fun testModalMessage() {
        val rule = provider.impressionRules(
            InAppMessage(
                name = "woot",
                displayContent = InAppMessageDisplayContent.ModalContent(
                    Modal(buttons = emptyList(), template = Modal.Template.HEADER_BODY_MEDIA)
                )
            )
        )
        assertEquals(InAppDisplayImpressionRule.Once, rule)
    }

    @Test
    public fun testBannerMessage() {
        val rule = provider.impressionRules(
            InAppMessage(
                name = "woot",
                displayContent = InAppMessageDisplayContent.BannerContent(
                    Banner(template = Banner.Template.MEDIA_LEFT, placement = Banner.Placement.BOTTOM)
                )
            )
        )
        assertEquals(InAppDisplayImpressionRule.Once, rule)
    }

    @Test
    public fun testModalThomas() {
        val airshipLayout = """
          {
            "layout":{
              "version":1,
              "presentation":{
                 "type":"modal",
                 "default_placement":{
                    "size":{
                       "width":"50%",
                       "height":"50%"
                    }
                 }
              },
              "view":{
                 "type":"container",
                 "items":[]
              }
            }
          }
        """.trimIndent()

        val rule = provider.impressionRules(
            InAppMessage(
                name = "woot",
                displayContent = InAppMessageDisplayContent.AirshipLayoutContent(
                    AirshipLayout.fromJson(JsonValue.parseString(airshipLayout))
                )
            )
        )
        assertEquals(InAppDisplayImpressionRule.Once, rule)
    }

    @Test
    public fun testBannerThomas() {
        val airshipLayout = """
          {
            "layout":{
              "version":1,
              "presentation":{
                 "type":"banner",
                 "default_placement":{
                    "position": "top",
                    "size":{
                       "width":"50%",
                       "height":"50%"
                    }
                 }
              },
              "view":{
                 "type":"container",
                 "items":[]
              }
            }
          }
        """.trimIndent()

        val rule = provider.impressionRules(
            InAppMessage(
                name = "woot",
                displayContent = InAppMessageDisplayContent.AirshipLayoutContent(
                    AirshipLayout.fromJson(JsonValue.parseString(airshipLayout))
                )
            )
        )
        assertEquals(InAppDisplayImpressionRule.Once, rule)
    }

    @Test
    public fun testEmbeddedThomas() {
        val airshipLayout = """
          {
            "layout":{
              "version":1,
              "presentation":{
                 "type":"embedded",
                 "embedded_id":"home_banner",
                 "default_placement":{
                    "size":{
                       "width":"50%",
                       "height":"50%"
                    }
                 }
              },
              "view":{
                 "type":"container",
                 "items":[]
              }
            }
          }
        """.trimIndent()

        val rule = provider.impressionRules(
            InAppMessage(
                name = "woot",
                displayContent = InAppMessageDisplayContent.AirshipLayoutContent(
                    AirshipLayout.fromJson(JsonValue.parseString(airshipLayout))
                )
            )
        )
        assertEquals(InAppDisplayImpressionRule.Interval(30.minutes), rule)
    }
}
