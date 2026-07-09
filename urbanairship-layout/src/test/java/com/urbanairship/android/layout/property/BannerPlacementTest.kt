/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.BannerPresentation
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BannerPlacementTest {

    @Test
    public fun testParsing() {
        val json = """
            {
              "size": {
                "width": "100%",
                "height": "auto"
              },
              "position": {
                "horizontal": "end",
                "vertical": "top"
              },
              "animation": {
                "type": "slide",
                "animate_in_seconds": 0.25,
                "animate_out_seconds": 0.5
              },
              "swipe_to_dismiss": true,
              "shadow": {
                "selectors": [
                  {
                    "platform": "android",
                    "shadow": {
                      "android_shadow": {
                        "elevation": 8,
                        "color": {
                          "default": {
                            "type": "hex",
                            "hex": "#000000",
                            "alpha": 0.4
                          }
                        }
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val placement = BannerPlacement.fromJson(JsonValue.parseString(json))

        assertEquals(HorizontalPosition.END, placement.position.horizontal)
        assertEquals(VerticalPosition.TOP, placement.position.vertical)
        assertTrue(placement.swipeToDismiss)

        val animation = placement.animation
        assertTrue(animation is BannerAnimation.Slide)
        assertEquals(250L, animation.animateInMs)
        assertEquals(500L, animation.animateOutMs)

        val shadow = placement.shadow?.androidShadow
        assertNotNull(shadow)
        assertEquals(8f, requireNotNull(shadow).elevation)
    }

    @Test
    public fun testParsingDefaults() {
        val json = """
            {
              "size": {
                "width": "100%",
                "height": "auto"
              },
              "position": {
                "horizontal": "center",
                "vertical": "bottom"
              }
            }
        """.trimIndent()

        val placement = BannerPlacement.fromJson(JsonValue.parseString(json))

        assertEquals(HorizontalPosition.CENTER, placement.position.horizontal)
        assertEquals(VerticalPosition.BOTTOM, placement.position.vertical)
        assertTrue(placement.swipeToDismiss)
        assertEquals(BannerAnimation.DEFAULT, placement.animation)
        assertNull(placement.shadow)
    }

    @Test
    public fun testParsingSwipeToDismissDisabled() {
        val json = """
            {
              "size": {
                "width": "100%",
                "height": "auto"
              },
              "position": {
                "horizontal": "center",
                "vertical": "bottom"
              },
              "swipe_to_dismiss": false
            }
        """.trimIndent()

        val placement = BannerPlacement.fromJson(JsonValue.parseString(json))

        assertFalse(placement.swipeToDismiss)
    }

    @Test
    public fun testParsingLegacyStringPosition() {
        val json = """
            {
              "size": {
                "width": "100%",
                "height": "auto"
              },
              "position": "top"
            }
        """.trimIndent()

        val placement = BannerPlacement.fromJson(JsonValue.parseString(json))

        assertEquals(HorizontalPosition.CENTER, placement.position.horizontal)
        assertEquals(VerticalPosition.TOP, placement.position.vertical)
    }

    @Test
    public fun testCenterCenterPositionThrows() {
        val json = """
            {
              "size": {
                "width": "100%",
                "height": "auto"
              },
              "position": {
                "horizontal": "center",
                "vertical": "center"
              }
            }
        """.trimIndent()

        assertThrows(JsonException::class.java) {
            BannerPlacement.fromJson(JsonValue.parseString(json))
        }
    }

    @Test
    public fun testPresentationDurationSeconds() {
        val json = """
            {
              "type": "banner",
              "default_placement": {
                "size": {
                  "width": "100%",
                  "height": "auto"
                },
                "position": {
                  "horizontal": "center",
                  "vertical": "top"
                }
              },
              "duration_seconds": 7.5
            }
        """.trimIndent()

        val presentation = BannerPresentation.fromJson(JsonValue.parseString(json))
        assertEquals(7500L, presentation.durationMs)
    }

    @Test
    public fun testPresentationLegacyDurationMilliseconds() {
        val json = """
            {
              "type": "banner",
              "default_placement": {
                "size": {
                  "width": "100%",
                  "height": "auto"
                },
                "position": "bottom"
              },
              "duration_milliseconds": 7000
            }
        """.trimIndent()

        val presentation = BannerPresentation.fromJson(JsonValue.parseString(json))
        assertEquals(7000L, presentation.durationMs)
    }

    @Test
    public fun testPresentationNoDuration() {
        val json = """
            {
              "type": "banner",
              "default_placement": {
                "size": {
                  "width": "100%",
                  "height": "auto"
                },
                "position": "bottom"
              }
            }
        """.trimIndent()

        val presentation = BannerPresentation.fromJson(JsonValue.parseString(json))
        assertNull(presentation.durationMs)
    }
}
