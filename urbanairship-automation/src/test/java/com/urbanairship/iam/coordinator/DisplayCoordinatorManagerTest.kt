package com.urbanairship.iam.coordinator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestActivityMonitor
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonValue
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DisplayCoordinatorManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStore = PreferenceStore.inMemoryStore(context)
    private val activityMonitor: ActivityMonitor = mockk(relaxed = true)
    private val manager = DisplayCoordinatorManager(dataStore, activityMonitor)

    @Test
    public fun testDefaultAdapter() {
        val message = InAppMessage("test", InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL)))
        val coordinator = manager.displayCoordinator(message)
        assertTrue(coordinator is DefaultDisplayCoordinator)
    }

    @Test
    public fun testDefaultAdapterEmbedded() {
        val layout = """
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

        val message = InAppMessage(
            name = "message",
            displayContent = InAppMessageDisplayContent.AirshipLayoutContent(AirshipLayout.fromJson(JsonValue.parseString(layout))))

        val coordinator = manager.displayCoordinator(message)
        assertTrue(coordinator is EmbeddedDisplayCoordinator)
    }

    @Test
    public fun testStandardBehavior() {
        val message = InAppMessage(
            name = "test",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL)),
            displayBehavior = InAppMessage.DisplayBehavior.STANDARD
        )

        val coordinator = manager.displayCoordinator(message)
        assertTrue(coordinator is DefaultDisplayCoordinator)
    }

    @Test
    public fun testImmediateBehavior() {
        val message = InAppMessage(
            name = "test",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL)),
            displayBehavior = InAppMessage.DisplayBehavior.IMMEDIATE
        )

        val coordinator = manager.displayCoordinator(message)
        assertTrue(coordinator is ImmediateDisplayCoordinator)
    }

    @Test
    public fun testImmediateDisplayBlocksDefault() {
        val tracker = DisplayActivityTracker()
        val appMonitor = TestActivityMonitor().also { it.foreground() }
        val immediate = ImmediateDisplayCoordinator(appMonitor, tracker)
        val default = DefaultDisplayCoordinator(
            displayInterval = 0.seconds,
            activityMonitor = appMonitor,
            activityTracker = tracker
        )

        val manager = DisplayCoordinatorManager(
            dataStore = dataStore,
            activityMonitor = appMonitor,
            activityTracker = tracker,
            defaultCoordinator = default,
            immediateCoordinator = immediate
        )

        val message = InAppMessage(
            name = "test",
            displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.NULL)),
            displayBehavior = InAppMessage.DisplayBehavior.IMMEDIATE
        )

        val selected = manager.displayCoordinator(message)
        assertTrue(selected is ImmediateDisplayCoordinator)
        assertTrue(default.isReady.value)

        selected.messageWillDisplay(message, "id-1")
        assertTrue(selected.isReady.value)
        assertFalse(default.isReady.value)

        selected.messageFinishedDisplaying(message, "id-1")
        assertTrue(default.isReady.value)
    }

    @Test
    public fun testEmbeddedDisplayDoesNotBlockDefault() {
        val tracker = DisplayActivityTracker()
        val appMonitor = TestActivityMonitor().also { it.foreground() }
        val embedded = EmbeddedDisplayCoordinator(appMonitor)
        val default = DefaultDisplayCoordinator(
            displayInterval = 0.seconds,
            activityMonitor = appMonitor,
            activityTracker = tracker
        )

        val manager = DisplayCoordinatorManager(
            dataStore = dataStore,
            activityMonitor = appMonitor,
            activityTracker = tracker,
            defaultCoordinator = default,
            embeddedCoordinator = embedded
        )

        val layout = """
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

        val message = InAppMessage(
            name = "message",
            displayContent = InAppMessageDisplayContent.AirshipLayoutContent(
                AirshipLayout.fromJson(JsonValue.parseString(layout))
            )
        )

        val selected = manager.displayCoordinator(message)
        assertTrue(selected is EmbeddedDisplayCoordinator)
        selected.messageWillDisplay(message, "id-1")

        assertTrue(default.isReady.value)
    }
}
