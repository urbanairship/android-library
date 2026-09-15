/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class UrlInfoTest {

    /**
     * Banner nubs are authored as stack_image_view content, so image_url items in a
     * stack_image_view must be included in URL extraction for prefetch.
     */
    @Test
    public fun testBannerStackImageViewImageUrlIsExtracted() {
        val layout = LayoutInfo(JsonValue.parseString(BANNER_NUB_LAYOUT).optMap())

        val urls = UrlInfo.from(layout.view)

        assertTrue(urls.any {
            it.type == UrlInfo.UrlType.IMAGE && it.url == "https://example.com/nub.png"
        })
    }

    private companion object {
        // Adapted from the devapp MOBILE-5724-nub-stack-image-view.yaml sample, with an
        // image_url item added alongside the shape item.
        private const val BANNER_NUB_LAYOUT = """
            {
              "version": 1,
              "presentation": {
                "type": "banner",
                "default_placement": {
                  "size": { "width": "100%", "height": "auto" },
                  "position": { "horizontal": "center", "vertical": "bottom" }
                }
              },
              "view": {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [
                  {
                    "size": { "width": 36, "height": 5 },
                    "view": {
                      "type": "stack_image_view",
                      "identifier": "banner_nub",
                      "items": [
                        {
                          "type": "shape",
                          "shape": {
                            "type": "rectangle",
                            "aspect_ratio": 7.2,
                            "scale": 1,
                            "color": { "default": { "type": "hex", "hex": "#B0B3C1", "alpha": 1 } },
                            "border": { "radius": 3 }
                          }
                        },
                        {
                          "type": "image_url",
                          "url": "https://example.com/nub.png",
                          "media_fit": "center_inside"
                        }
                      ]
                    }
                  },
                  {
                    "size": { "width": "100%", "height": "auto" },
                    "view": {
                      "type": "label",
                      "text": "Banner content",
                      "text_appearance": {
                        "font_size": 15,
                        "alignment": "center",
                        "color": { "default": { "type": "hex", "hex": "#1A1A2E", "alpha": 1 } }
                      }
                    }
                  }
                ]
              }
            }
        """
    }
}
