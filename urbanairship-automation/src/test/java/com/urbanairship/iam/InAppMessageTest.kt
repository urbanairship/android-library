package com.urbanairship.iam

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.Fullscreen
import com.urbanairship.iam.content.HTML
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.content.Modal
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageButtonLayoutType
import com.urbanairship.iam.info.InAppMessageColor
import com.urbanairship.iam.info.InAppMessageMediaInfo
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppMessageTest {

    @Test
    public fun testBanner() {
        val json = """
            {
             "source": "remote-data",
             "display" : {
                "allow_fullscreen_display" : true,
                "background_color" : "#ffffff",
                "body" : {
                   "alignment" : "center",
                   "color" : "#000000",
                   "font_family" : [
                      "sans-serif"
                   ],
                   "size" : 16,
                   "text" : "Big body"
                },
                "border_radius" : 5,
                "button_layout" : "stacked",
                "buttons" : [
                   {
                      "background_color" : "#63aff2",
                      "border_color" : "#63aff2",
                      "border_radius" : 2,
                      "id" : "d17a055c-ed67-4101-b65f-cd28b5904c84",
                      "label" : {
                         "color" : "#ffffff",
                         "font_family" : [
                            "sans-serif"
                         ],
                         "size" : 10,
                         "style" : [
                            "bold"
                         ],
                         "text" : "Touch it"
                      }
                   }
                ],
                "dismiss_button_color" : "#000000",
                "heading" : {
                   "alignment" : "center",
                   "color" : "#63aff2",
                   "font_family" : [
                      "sans-serif"
                   ],
                   "size" : 22,
                   "text" : "Boom"
                },
                "media" : {
                   "description" : "Image",
                   "type" : "image",
                   "url" : "some://image"
                },
                "template" : "media_left",
                "placement" : "top",
                "duration" : 100.0
             },
             "display_type" : "banner",
             "name" : "woot"
          }
        """.trimIndent()

        val expected = InAppMessage(
            name = "woot",
            displayContent = InAppMessageDisplayContent.BannerContent(
                Banner(
                    heading = InAppMessageTextInfo(
                        text = "Boom",
                        color = InAppMessageColor(Color.parseColor("#63aff2")),
                        size = 22F,
                        fontFamilies = listOf("sans-serif"),
                        alignment = InAppMessageTextInfo.Alignment.CENTER
                    ),
                    body = InAppMessageTextInfo(
                        text = "Big body",
                        color = InAppMessageColor(Color.parseColor("#000000")),
                        size = 16.0F,
                        fontFamilies = listOf("sans-serif"),
                        alignment = InAppMessageTextInfo.Alignment.CENTER
                    ),
                    media = InAppMessageMediaInfo(
                        url = "some://image",
                        type = InAppMessageMediaInfo.MediaType.IMAGE,
                        description = "Image"
                    ),
                    buttons = listOf(
                        InAppMessageButtonInfo(
                            identifier = "d17a055c-ed67-4101-b65f-cd28b5904c84",
                            label = InAppMessageTextInfo(
                                text = "Touch it",
                                color = InAppMessageColor(Color.parseColor("#ffffff")),
                                size = 10F,
                                fontFamilies = listOf("sans-serif"),
                                style = listOf(InAppMessageTextInfo.Style.BOLD)
                            ),
                            backgroundColor = InAppMessageColor(Color.parseColor("#63aff2")),
                            borderColor = InAppMessageColor(Color.parseColor("#63aff2")),
                            borderRadius = 2F
                        )
                    ),
                    buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
                    template = Banner.Template.MEDIA_LEFT,
                    backgroundColor = InAppMessageColor(Color.parseColor("#ffffff")),
                    dismissButtonColor = InAppMessageColor(Color.parseColor("#000000")),
                    borderRadius = 5F,
                    durationMs = 100,
                    placement = Banner.Placement.TOP
                )
            ),
            source =  InAppMessage.Source.REMOTE_DATA,
        )

        verify(json, expected)
    }

    @Test
    public fun testModal() {
        val json = """
          {
            "source": "app-defined",
            "display": {
                "allow_fullscreen_display": true,
                "background_color": "#ffffff",
                "body": {
                    "alignment": "center",
                    "color": "#000000",
                    "font_family": [
                        "sans-serif"
                    ],
                    "size": 16,
                    "text": "Big body"
                },
                "border_radius": 5,
                "button_layout": "stacked",
                "buttons": [
                    {
                        "background_color": "#63aff2",
                        "border_color": "#63aff2",
                        "border_radius": 2,
                        "id": "d17a055c-ed67-4101-b65f-cd28b5904c84",
                        "label": {
                            "color": "#ffffff",
                            "font_family": [
                                "sans-serif"
                            ],
                            "size": 10,
                            "style": [
                                "bold"
                            ],
                            "text": "Touch it"
                        }
                    }
                ],
                "dismiss_button_color": "#000000",
                "heading": {
                    "alignment": "center",
                    "color": "#63aff2",
                    "font_family": [
                        "sans-serif"
                    ],
                    "size": 22,
                    "text": "Boom"
                },
                "media": {
                    "description": "Image",
                    "type": "image",
                    "url": "some://image"
                },
                "template": "media_header_body"
            },
            "display_type": "modal",
            "name": "woot"
        }
        """.trimIndent()

        val expected = InAppMessage(
            name = "woot",
            displayContent = InAppMessageDisplayContent.ModalContent(
                Modal(
                    heading = InAppMessageTextInfo(
                        text = "Boom",
                        color = InAppMessageColor(Color.parseColor("#63aff2")),
                        size = 22.0f,
                        fontFamilies = listOf("sans-serif"),
                        alignment = InAppMessageTextInfo.Alignment.CENTER
                    ),
                    body = InAppMessageTextInfo(
                        text = "Big body",
                        color = InAppMessageColor(Color.parseColor("#000000")),
                        size = 16.0F,
                        fontFamilies = listOf("sans-serif"),
                        alignment = InAppMessageTextInfo.Alignment.CENTER
                    ),
                    media = InAppMessageMediaInfo(
                        url = "some://image",
                        type = InAppMessageMediaInfo.MediaType.IMAGE,
                        description = "Image"
                    ),
                    buttons = listOf(
                        InAppMessageButtonInfo(
                            identifier = "d17a055c-ed67-4101-b65f-cd28b5904c84",
                            label = InAppMessageTextInfo(
                                text = "Touch it",
                                color = InAppMessageColor(Color.parseColor("#ffffff")),
                                size = 10F,
                                fontFamilies = listOf("sans-serif"),
                                style = listOf(InAppMessageTextInfo.Style.BOLD)
                            ),
                            backgroundColor = InAppMessageColor(Color.parseColor("#63aff2")),
                            borderColor = InAppMessageColor(Color.parseColor("#63aff2")),
                            borderRadius = 2F
                        )
                    ),
                    buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
                    template = Modal.Template.MEDIA_HEADER_BODY,
                    dismissButtonColor = InAppMessageColor(Color.parseColor("#000000")),
                    backgroundColor = InAppMessageColor(Color.parseColor("#ffffff")),
                    allowFullscreenDisplay = true
                )
            ),
            source =  InAppMessage.Source.APP_DEFINED
        )

        verify(json, expected)
    }

    @Test
    public fun testFullscreen() {
        val json = """
            {
             "source": "app-defined",
             "display" : {
                "background_color" : "#ffffff",
                "body" : {
                   "alignment" : "center",
                   "color" : "#000000",
                   "font_family" : [
                      "sans-serif"
                   ],
                   "size" : 16,
                   "text" : "Big body"
                },
                "button_layout" : "stacked",
                "buttons" : [
                   {
                      "background_color" : "#63aff2",
                      "border_color" : "#63aff2",
                      "border_radius" : 2,
                      "id" : "d17a055c-ed67-4101-b65f-cd28b5904c84",
                      "label" : {
                         "color" : "#ffffff",
                         "font_family" : [
                            "sans-serif"
                         ],
                         "size" : 10,
                         "style" : [
                            "bold"
                         ],
                         "text" : "Touch it"
                      }
                   }
                ],
                "dismiss_button_color" : "#000000",
                "heading" : {
                   "alignment" : "center",
                   "color" : "#63aff2",
                   "font_family" : [
                      "sans-serif"
                   ],
                   "size" : 22,
                   "text" : "Boom"
                },
                "media" : {
                   "description" : "Image",
                   "type" : "image",
                   "url" : "some://image"
                },
                "template" : "media_header_body"
             },
             "display_type" : "fullscreen",
             "name" : "woot"
          }
        """.trimIndent()

        val expected = InAppMessage(
            name = "woot",
            displayContent = InAppMessageDisplayContent.FullscreenContent(
                Fullscreen(
                    heading = InAppMessageTextInfo(
                        text = "Boom",
                        color = InAppMessageColor(Color.parseColor("#63aff2")),
                        size = 22.0F,
                        fontFamilies = listOf("sans-serif"),
                        alignment = InAppMessageTextInfo.Alignment.CENTER
                    ),
                    body = InAppMessageTextInfo(
                        text = "Big body",
                        color = InAppMessageColor(Color.parseColor("#000000")),
                        size = 16.0F,
                        fontFamilies = listOf("sans-serif"),
                        alignment = InAppMessageTextInfo.Alignment.CENTER
                    ),
                    media = InAppMessageMediaInfo(
                        url = "some://image",
                        type = InAppMessageMediaInfo.MediaType.IMAGE,
                        description = "Image"
                    ),
                    buttons = listOf(
                        InAppMessageButtonInfo(
                            identifier = "d17a055c-ed67-4101-b65f-cd28b5904c84",
                            label = InAppMessageTextInfo(
                                text = "Touch it",
                                color = InAppMessageColor(Color.parseColor("#ffffff")),
                                size = 10F,
                                fontFamilies = listOf("sans-serif"),
                                style = listOf(InAppMessageTextInfo.Style.BOLD)
                            ),
                            backgroundColor = InAppMessageColor(Color.parseColor("#63aff2")),
                            borderColor = InAppMessageColor(Color.parseColor("#63aff2")),
                            borderRadius = 2F
                        )
                    ),
                    buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
                    template = Fullscreen.Template.MEDIA_HEADER_BODY,
                    dismissButtonColor = InAppMessageColor(Color.parseColor("#000000")),
                    backgroundColor = InAppMessageColor(Color.parseColor("#ffffff")),
                    footer = null
                )
            ),
            source =  InAppMessage.Source.APP_DEFINED
        )

        verify(json, expected)
    }

    @Test
    public fun testHTML() {
        val json = """
            {
             "display" : {
                "allow_fullscreen_display" : false,
                "background_color" : "#00000000",
                "border_radius" : 5,
                "dismiss_button_color" : "#000000",
                "url" : "some://url"
             },
             "display_type" : "html",
             "name" : "Thanks page"
         }
        """.trimIndent()

        val expected = InAppMessage(
            name = "Thanks page",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML(
                    url = "some://url",
                    dismissButtonColor = InAppMessageColor(Color.parseColor("#000000")),
                    backgroundColor = InAppMessageColor(Color.parseColor("#00000000")),
                    borderRadius = 5.0F,
                    allowFullscreenDisplay = false
                )
            ),
            source = null
        )

        verify(json, expected)
    }

    @Test
    public fun testCustom() {
        val json = """
            {
             "source": "app-defined",
             "display" : {
                "cool": "story"
             },
             "display_type" : "custom",
             "name" : "woot"
          }
        """.trimIndent()

        val expected = InAppMessage(
            name = "woot",
            displayContent = InAppMessageDisplayContent.CustomContent(
                Custom(jsonMapOf("cool" to "story").toJsonValue())
            ),
            source =  InAppMessage.Source.APP_DEFINED
        )

        verify(json, expected)
    }

    @Test
    public fun testAirshipLayout() {
        val layout = """
            {
                "layout": {
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

        val json = """
            {
             "source": "remote-data",
             "display" : $layout,
             "display_type" : "layout",
             "name" : "Airship layout"
          }
        """.trimIndent()

        val expectedLayout = AirshipLayout.fromJson(JsonValue.parseString(layout))
        val expected = InAppMessage(
            name = "Airship layout",
            displayContent = InAppMessageDisplayContent.AirshipLayoutContent(
                expectedLayout
            ),
            source =  InAppMessage.Source.REMOTE_DATA
        )

        verify(json, expected)
    }

    private fun verify(json: String, expected: InAppMessage) {
        val fromJson = InAppMessage.parseJson(JsonValue.parseString(json))
        assertEquals(expected, fromJson)

        val roundTrip = InAppMessage.parseJson(fromJson.toJsonValue())
        assertEquals(fromJson, roundTrip)
    }
}
