/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A `backgrounds` media layer has to reach [UrlInfo] like any other media in the scene.
 *
 * This set is what gets prefetched before a scene is shown, and what decides whether it can be
 * shown at all without a network. Missing a layer means its image always misses the cache and
 * pops in late, and means a scene can be judged ready to display while the only thing behind its
 * content is still unfetched.
 */
@RunWith(RobolectricTestRunner::class)
public class UrlInfoBackgroundsTest {

    @Test
    public fun anImageLayerOnANonParallaxHostIsCollected() {
        val urls = urlsFor(
            """
            {
              "type": "container",
              "backgrounds": [
                { "type": "color", "color": { "default": { "hex": "#000000", "alpha": 1 } } },
                { "type": "media", "media_type": "image", "media_fit": "fit_crop",
                  "url": "https://example/layer.jpg" }
              ],
              "items": []
            }
            """
        )

        assertTrue(
            "the layer's image should be prefetched -- got $urls",
            urls.any { it.type == UrlInfo.UrlType.IMAGE && it.url == "https://example/layer.jpg" }
        )
    }

    /** A grid's cells and its own layers are both part of the scene. */
    @Test
    public fun aGridsItemsAndLayersAreCollected() {
        val urls = urlsFor(
            """
            {
              "type": "grid_layout",
              "identifier": "grid",
              "config": { "columns": { "type": "fixed", "count": 2 } },
              "backgrounds": [
                { "type": "media", "media_type": "video", "media_fit": "fit_crop",
                  "url": "https://example/grid-layer.mp4" }
              ],
              "items": [
                { "view": { "type": "media", "media_type": "image", "media_fit": "center_inside",
                            "url": "https://example/cell.jpg" } }
              ]
            }
            """
        )

        assertTrue(
            "the cell's image should be prefetched -- got $urls",
            urls.any { it.type == UrlInfo.UrlType.IMAGE && it.url == "https://example/cell.jpg" }
        )
        assertTrue(
            "and the grid's own video layer -- got $urls",
            urls.any { it.type == UrlInfo.UrlType.VIDEO && it.url == "https://example/grid-layer.mp4" }
        )
    }

    /** A parallax host states its layers differently, and must be walked just the same. */
    @Test
    public fun aVideoLayerOnAParallaxHostIsCollected() {
        val urls = urlsFor(
            """
            {
              "type": "scroll_layout",
              "direction": "vertical",
              "backgrounds": [
                { "type": "media", "media_type": "video", "media_fit": "fit_crop",
                  "url": "https://example/layer.mp4",
                  "parallax": { "factor": 0.4 } }
              ],
              "view": { "type": "linear_layout", "direction": "vertical", "items": [] }
            }
            """
        )

        assertTrue(
            "a video layer counts as media the scene needs -- got $urls",
            urls.any { it.type == UrlInfo.UrlType.VIDEO && it.url == "https://example/layer.mp4" }
        )
    }

    /** Hosts nest, and a layer deep in the tree is no less part of the scene. */
    @Test
    public fun aLayerOnANestedHostIsCollected() {
        val urls = urlsFor(
            """
            {
              "type": "container",
              "items": [
                { "position": { "horizontal": "center", "vertical": "center" },
                  "size": { "width": "100%", "height": "100%" },
                  "view": {
                    "type": "linear_layout",
                    "direction": "vertical",
                    "backgrounds": [
                      { "type": "media", "media_type": "image", "media_fit": "fit_crop",
                        "url": "https://example/nested.jpg" }
                    ],
                    "items": []
                  } }
              ]
            }
            """
        )

        assertTrue(
            "recursion into children has to carry backgrounds with it -- got $urls",
            urls.any { it.url == "https://example/nested.jpg" }
        )
    }

    private fun urlsFor(json: String): Set<UrlInfo> =
        UrlInfo.from(ViewInfo.viewInfoFromJson(JsonValue.parseString(json.trimIndent()).requireMap()))
}
