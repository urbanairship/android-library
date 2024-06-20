package com.urbanairship.android.layout.ui

import android.os.Parcel
import com.urbanairship.TestActivityMonitor
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.display.DisplayArgsLoader
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.webkit.AirshipWebViewClient
import io.mockk.mockk
import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class DisplayArgsLoaderTest : TestCase() {

    private val listener = mockk<ThomasListenerInterface>()

    private val activityMonitor = TestActivityMonitor()

    private lateinit var layoutInfo: LayoutInfo

    @Before
    @Throws(JsonException::class)
    public fun setup() {
        val payloadString = """{
            "layout": {
                "version": 1,
                "presentation": {
                  "type": "modal",
                  "default_placement": {
                    "size": {
                      "width": "100%",
                      "height": "100%"
                    },
                    "position": {
                        "horizontal": "center",
                        "vertical": "center"
                    },
                    "shade_color": {
                      "default": {
                          "type": "hex",
                          "hex": "#000000",
                          "alpha": 0.2 }
                    }
                  }
                },
                "view": {
                    "type": "empty_view"
                }
            }
        }""".trimIndent()

        val payload = JsonValue.parseString(payloadString)
        layoutInfo = LayoutInfo(payload.optMap().opt("layout").optMap())
    }

    @Test
    @Throws(DisplayArgsLoader.LoadException::class)
    public fun testParcelable() {
        val imageCache = ImageCache { null }
        val clientFactory: Factory<AirshipWebViewClient> = Factory { AirshipWebViewClient() }
        val displayArgs = DisplayArgs(layoutInfo, listener, activityMonitor,  mockk(), clientFactory, imageCache)
        val loader: DisplayArgsLoader = DisplayArgsLoader.newLoader(displayArgs)

        // Write
        val parcel = Parcel.obtain()
        loader.writeToParcel(parcel, 0)

        // Reset the parcel so we can read it
        parcel.setDataPosition(0)

        // Read
        val fromParcel: DisplayArgsLoader = DisplayArgsLoader.CREATOR.createFromParcel(parcel)
        assertEquals(loader.displayArgs.payload, fromParcel.displayArgs.payload)
        assertEquals(
            loader.displayArgs.listener,
            fromParcel.displayArgs.listener
        )
        assertEquals(
            loader.displayArgs.imageCache,
            fromParcel.displayArgs.imageCache
        )
        assertEquals(
            loader.displayArgs.webViewClientFactory,
            fromParcel.displayArgs.webViewClientFactory
        )
    }

    @Test(expected = DisplayArgsLoader.LoadException::class)
    @Throws(DisplayArgsLoader.LoadException::class)
    public fun testDismiss() {
        val displayArgs = DisplayArgs(layoutInfo, listener, activityMonitor,  mockk(), null, null)
        val loader: DisplayArgsLoader = DisplayArgsLoader.newLoader(displayArgs)
        loader.dispose()
        loader.displayArgs
    }

    @Test(expected = DisplayArgsLoader.LoadException::class)
    @Throws(DisplayArgsLoader.LoadException::class)
    public fun testDismissParcel() {
        val displayArgs = DisplayArgs(layoutInfo, listener, activityMonitor,  mockk(), null, null)
        val loader: DisplayArgsLoader = DisplayArgsLoader.newLoader(displayArgs)
        val parcel = Parcel.obtain()
        loader.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val fromParcel: DisplayArgsLoader = DisplayArgsLoader.CREATOR.createFromParcel(parcel)
        loader.dispose()
        fromParcel.displayArgs
    }
}
