/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasCapabilities
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.property.Background
import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.images.ImageLoader
import com.urbanairship.images.ImageRequestOptions
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A `backgrounds` image layer answers to `view_overrides`, the same as a video one.
 *
 * A video layer got this for free: it renders through a real `MediaModel`/`MediaView`, which
 * resolves its url against the scene's state on every update. An image layer is painted here
 * instead, and used to parse only four fields -- so a scene that swapped its backdrop on form
 * state worked or did nothing depending on the layer's `media_type`, with no error either way.
 * Both kinds now carry the same `MediaInfo` and resolve through it.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class BackgroundsViewOverridesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true) {
        every { imageCache() } returns null
    }
    private val loader = RecordingImageLoader()

    @Before
    public fun setUp() {
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns loader
    }

    @After
    public fun tearDown() {
        unmockkStatic(Airship::class)
        unmockkObject(Airship)
    }

    @Test
    public fun anImageLayerLoadsItsDeclaredUrlBeforeAnyStateArrives() {
        painter().draw(Canvas(bitmap()), BOX, BOX)

        assertEquals(listOf(DECLARED_URL), loader.requested)
    }

    @Test
    public fun anImageLayerFollowsAStateThatOverridesItsUrl() {
        val painter = painter()
        painter.draw(Canvas(bitmap()), BOX, BOX)

        painter.state = stateWhere(THEME to "dark")

        assertEquals(
            "the override should have been loaded into the layer's own view",
            listOf(DECLARED_URL, OVERRIDE_URL), loader.requested
        )
    }

    /**
     * State changes on every form edit, and a layer that reloaded each time would drop the image
     * it already has for an identical one.
     */
    @Test
    public fun aStateThatSelectsNoOverrideLeavesTheLayerAlone() {
        val painter = painter()
        painter.draw(Canvas(bitmap()), BOX, BOX)

        painter.state = stateWhere(THEME to "light")
        painter.state = stateWhere(THEME to "light")

        assertEquals(listOf(DECLARED_URL), loader.requested)
    }

    /** And back again: the layer follows the state rather than latching onto the override. */
    @Test
    public fun aLayerReturnsToItsDeclaredUrlWhenTheOverrideStopsMatching() {
        val painter = painter()
        painter.draw(Canvas(bitmap()), BOX, BOX)
        painter.state = stateWhere(THEME to "dark")

        painter.state = stateWhere(THEME to "light")

        assertEquals(listOf(DECLARED_URL, OVERRIDE_URL, DECLARED_URL), loader.requested)
    }

    /**
     * A url a layer can switch to has to be in the prefetch set too -- it is as much a part of
     * the scene as the one the layer starts on, and is needed the instant the state changes.
     */
    @Test
    public fun anOverrideUrlIsCollectedForPrefetch() {
        val urls = UrlInfo.from(
            ViewInfo.viewInfoFromJson(
                JsonValue.parseString(
                    """
                    {
                      "type": "container",
                      "backgrounds": [ $IMAGE_LAYER ],
                      "items": []
                    }
                    """.trimIndent()
                ).requireMap()
            )
        )

        assertTrue(
            "a url the layer can switch to should be fetched ahead of it -- got $urls",
            urls.any { it.url == OVERRIDE_URL }
        )
        assertTrue(urls.any { it.url == DECLARED_URL })
    }

    private fun painter(): BackgroundsPainter =
        BackgroundsPainter(context, viewEnvironment) {}.apply {
            backgrounds = Background.fromJsonList(
                JsonValue.parseString("[ $IMAGE_LAYER ]").requireList()
            )
        }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(BOX, BOX, Bitmap.Config.ARGB_8888)

    /** A state whose layout scope holds [entry], which is what an override matches against. */
    private fun stateWhere(entry: Pair<String, String>): ThomasState = ThomasState(
        layout = State.Layout(identifier = "test-layout").apply {
            state = mapOf(entry.first to JsonValue.wrap(entry.second))
        },
        form = null,
        pager = null,
        video = null,
        asyncView = null,
        capabilities = ThomasCapabilities.NONE
    )

    private class RecordingImageLoader : ImageLoader {
        val requested: MutableList<String> = mutableListOf()

        override fun load(
            context: Context,
            imageView: ImageView,
            imageRequestOptions: ImageRequestOptions
        ) {
            requested.add(imageRequestOptions.url.orEmpty())
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            imageView.setImageDrawable(BitmapDrawable(context.resources, bitmap))
            imageRequestOptions.callback?.onImageLoaded(true)
        }
    }

    private companion object {
        private const val BOX = 100
        private const val THEME = "theme"
        private const val DECLARED_URL = "https://example/declared.jpg"
        private const val OVERRIDE_URL = "https://example/override.jpg"

        private val IMAGE_LAYER = """
            {
              "type": "media", "media_type": "image", "media_fit": "fit_crop",
              "url": "$DECLARED_URL",
              "view_overrides": {
                "url": [
                  {
                    "when_state_matches": {
                      "scope": ["$THEME"],
                      "value": { "equals": "dark" }
                    },
                    "value": "$OVERRIDE_URL"
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
