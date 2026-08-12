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
import kotlin.time.Duration.Companion.milliseconds
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
        assertEquals(250.milliseconds, animation?.animateIn)
        assertEquals(500.milliseconds, animation?.animateOut)

        val shadow = placement.shadow?.androidShadow
        assertNotNull(shadow)
        assertEquals(8f, requireNotNull(shadow).elevation)
    }

    @Test
    public fun testParsingFadeAnimation() {
        val placement = BannerPlacement.fromJson(placementJson(
            """
            "position": { "horizontal": "center", "vertical": "bottom" },
            "animation": {
              "type": "fade",
              "animate_in_seconds": 0.4,
              "animate_out_seconds": 0.2
            }
            """
        ))

        val animation = placement.animation
        assertTrue(animation is BannerAnimation.Fade)
        assertEquals(400.milliseconds, animation?.animateIn)
        assertEquals(200.milliseconds, animation?.animateOut)
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
        assertNull(placement.animation)
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
    public fun testParsingSwipeToDismissDefaultsTrue() {
        val json = """
            {
              "size": {
                "width": "100%",
                "height": "auto"
              },
              "position": { "horizontal": "center", "vertical": "top" }
            }
        """.trimIndent()

        val placement = BannerPlacement.fromJson(JsonValue.parseString(json))

        assertTrue(placement.swipeToDismiss)
    }

    @Test
    public fun testParsingVerticalCenterEdgePositions() {
        // Vertically centered edge placements drive the horizontal swipe axis, so both valid
        // combinations must parse.
        val start = BannerPlacement.fromJson(placementJson(
            """"position": { "horizontal": "start", "vertical": "center" }"""
        ))
        assertEquals(HorizontalPosition.START, start.position.horizontal)
        assertEquals(VerticalPosition.CENTER, start.position.vertical)

        val end = BannerPlacement.fromJson(placementJson(
            """"position": { "horizontal": "end", "vertical": "center" }"""
        ))
        assertEquals(HorizontalPosition.END, end.position.horizontal)
        assertEquals(VerticalPosition.CENTER, end.position.vertical)
    }

    @Test
    public fun testUnknownAnimationTypeThrows() {
        val json = placementJson(
            """"position": { "horizontal": "center", "vertical": "top" }, "animation": { "type": "zoom" }"""
        )

        assertThrows(JsonException::class.java) {
            BannerPlacement.fromJson(json)
        }
    }

    @Test
    public fun testShadowIosOnlySelectorIsIgnored() {
        val json = placementJson(
            """
            "position": { "horizontal": "center", "vertical": "top" },
            "shadow": {
              "selectors": [
                {
                  "platform": "ios",
                  "shadow": {
                    "android_shadow": {
                      "elevation": 8,
                      "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 0.4 } }
                    }
                  }
                }
              ]
            }
            """
        )

        val placement = BannerPlacement.fromJson(json)

        assertNull(placement.shadow)
    }

    @Test
    public fun testShadowSelectorWithoutPlatformMatches() {
        val json = placementJson(
            """
            "position": { "horizontal": "center", "vertical": "top" },
            "shadow": {
              "selectors": [
                {
                  "shadow": {
                    "android_shadow": {
                      "elevation": 4,
                      "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 0.4 } }
                    }
                  }
                }
              ]
            }
            """
        )

        val placement = BannerPlacement.fromJson(json)

        assertEquals(4f, placement.shadow?.androidShadow?.elevation)
    }

    @Test
    public fun testShadowFirstMatchingSelectorWins() {
        val json = placementJson(
            """
            "position": { "horizontal": "center", "vertical": "top" },
            "shadow": {
              "selectors": [
                {
                  "platform": "ios",
                  "shadow": {
                    "android_shadow": {
                      "elevation": 2,
                      "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 0.4 } }
                    }
                  }
                },
                {
                  "shadow": {
                    "android_shadow": {
                      "elevation": 4,
                      "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 0.4 } }
                    }
                  }
                },
                {
                  "platform": "android",
                  "shadow": {
                    "android_shadow": {
                      "elevation": 8,
                      "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 0.4 } }
                    }
                  }
                }
              ]
            }
            """
        )

        val placement = BannerPlacement.fromJson(json)

        // The iOS selector is skipped, and the first usable selector wins over later matches.
        assertEquals(4f, placement.shadow?.androidShadow?.elevation)
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
                "position": { "horizontal": "center", "vertical": "bottom" }
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
                "position": { "horizontal": "center", "vertical": "bottom" }
              }
            }
        """.trimIndent()

        val presentation = BannerPresentation.fromJson(JsonValue.parseString(json))
        assertNull(presentation.durationMs)
    }

    @Test
    public fun testPresentationNegativeDurationIsNull() {
        val presentation = BannerPresentation.fromJson(presentationJson("\"duration_seconds\": -5"))
        assertNull(presentation.durationMs)
    }

    @Test
    public fun testPresentationZeroDurationIsNull() {
        val presentation = BannerPresentation.fromJson(presentationJson("\"duration_seconds\": 0"))
        assertNull(presentation.durationMs)
    }

    @Test
    public fun testPresentationWrongTypedDurationIsNull() {
        val presentation = BannerPresentation.fromJson(presentationJson("\"duration_seconds\": \"7\""))
        assertNull(presentation.durationMs)
    }

    @Test
    public fun testPresentationNegativeLegacyDurationIsNull() {
        val presentation = BannerPresentation.fromJson(presentationJson("\"duration_milliseconds\": -7000"))
        assertNull(presentation.durationMs)
    }

    @Test
    public fun testPresentationZeroLegacyDurationIsNull() {
        val presentation = BannerPresentation.fromJson(presentationJson("\"duration_milliseconds\": 0"))
        assertNull(presentation.durationMs)
    }

    @Test
    public fun testPresentationWrongTypedLegacyDurationIsNull() {
        val presentation = BannerPresentation.fromJson(presentationJson("\"duration_milliseconds\": \"7000\""))
        assertNull(presentation.durationMs)
    }

    @Test
    public fun testPresentationUnusableDurationFallsBackToLegacy() {
        val presentation = BannerPresentation.fromJson(
            presentationJson("\"duration_seconds\": 0, \"duration_milliseconds\": 7000")
        )
        assertEquals(7000L, presentation.durationMs)
    }

    @Test
    public fun testPresentationDurationSecondsTakesPrecedence() {
        val presentation = BannerPresentation.fromJson(
            presentationJson("\"duration_seconds\": 5, \"duration_milliseconds\": 9000")
        )
        assertEquals(5000L, presentation.durationMs)
    }

    private fun placementJson(fields: String): JsonValue {
        val json = """
            {
              "size": {
                "width": "100%",
                "height": "auto"
              },
              $fields
            }
        """.trimIndent()
        return JsonValue.parseString(json)
    }

    private fun presentationJson(durationFields: String): JsonValue {
        val json = """
            {
              "type": "banner",
              "default_placement": {
                "size": {
                  "width": "100%",
                  "height": "auto"
                },
                "position": { "horizontal": "center", "vertical": "bottom" }
              },
              $durationFields
            }
        """.trimIndent()
        return JsonValue.parseString(json)
    }
}
