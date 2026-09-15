/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.screenshot

import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Rewrites a scene payload so it renders the same way every time, without the network.
 *
 * This mirrors `stubbingRemoteContent` in the iOS DevApp (`DevApp/Dev App/Thomas/Layouts.swift`).
 * Rewriting the payload before it is decoded, rather than stubbing the loader alone, is what lets a
 * screenshot exercise sizing and cropping for content the renderer could not otherwise draw here:
 *
 *  - image, video, YouTube and Vimeo media all become image media pointing at [PLACEHOLDER_URL], so
 *    a frame never lands at a random playback phase. On Android this is load-bearing rather than
 *    cosmetic: video/Vimeo/YouTube media and any `.svg` image are routed to a WebView by
 *    `MediaView`, and Robolectric draws no WebView content at all.
 *  - pager `automated_actions` are dropped, so stories hold their first page.
 *  - `randomize_children` is forced off, so option order is stable.
 *
 * `web_view` nodes are deliberately left alone. Their content cannot render, but keeping the node
 * still exercises the container geometry around it, which is the part a layout regression shows up
 * in. [Stubs.WEB_VIEW] records that so a green screenshot never claims more than it exercised.
 */
internal object SceneStubs {

    /** Any non-`.svg` URL works: [FakeImageLoader] ignores it and paints a fixed placeholder. */
    const val PLACEHOLDER_URL: String = "https://airship.test/uitest-placeholder.png"

    object Stubs {
        const val IMAGES = "images are placeholders"
        const val ANIMATED = "video shown as a placeholder image"
        const val WEB_VIEW = "web view renders blank"
        const val AUTO_ADVANCE = "auto-advance disabled, first page held"
        const val RANDOMIZED = "randomized child order fixed"
    }

    private val STILL_MEDIA = setOf("image")
    private val ANIMATED_MEDIA = setOf("video", "youtube", "vimeo")

    data class Result(val payload: JsonMap, val stubs: List<String>)

    fun apply(payload: JsonMap): Result {
        val stubs = linkedSetOf<String>()
        return Result(rewriteMap(payload, stubs), stubs.toList())
    }

    private fun rewriteValue(value: JsonValue, stubs: MutableSet<String>): JsonValue = when {
        value.isJsonMap -> JsonValue.wrap(rewriteMap(value.optMap(), stubs))
        value.isJsonList -> JsonValue.wrap(value.optList().list.map { rewriteValue(it, stubs) })
        else -> value
    }

    private fun rewriteMap(map: JsonMap, stubs: MutableSet<String>): JsonMap {
        val entries = map.map.toMutableMap()

        when (map.opt("type").string) {
            "media" -> {
                val mediaType = entries["media_type"]?.string
                if (mediaType in STILL_MEDIA || mediaType in ANIMATED_MEDIA) {
                    stubs += if (mediaType in ANIMATED_MEDIA) Stubs.ANIMATED else Stubs.IMAGES
                    entries["media_type"] = JsonValue.wrap("image")
                    entries["url"] = JsonValue.wrap(PLACEHOLDER_URL)
                    entries["url_selectors"]?.let { entries["url_selectors"] = rewriteSelectors(it) }
                }
            }
            "url" -> if (entries["url"]?.isString == true) {
                stubs += Stubs.IMAGES
                entries["url"] = JsonValue.wrap(PLACEHOLDER_URL)
            }
            "web_view" -> stubs += Stubs.WEB_VIEW
        }

        if (entries.remove("automated_actions") != null) {
            stubs += Stubs.AUTO_ADVANCE
        }

        if (entries["randomize_children"]?.getBoolean(false) == true) {
            stubs += Stubs.RANDOMIZED
            entries["randomize_children"] = JsonValue.wrap(false)
        }

        val builder = JsonMap.newBuilder()
        entries.forEach { (key, value) -> builder.put(key, rewriteValue(value, stubs)) }
        return builder.build()
    }

    /** A media node can carry per-locale or per-orientation URLs; all of them get the placeholder. */
    private fun rewriteSelectors(selectors: JsonValue): JsonValue =
        JsonValue.wrap(
            selectors.optList().list.map { selector ->
                val entries = selector.optMap().map.toMutableMap()
                entries["url"] = JsonValue.wrap(PLACEHOLDER_URL)
                val builder = JsonMap.newBuilder()
                entries.forEach { (key, value) -> builder.put(key, value) }
                JsonValue.wrap(builder.build())
            }
        )
}
