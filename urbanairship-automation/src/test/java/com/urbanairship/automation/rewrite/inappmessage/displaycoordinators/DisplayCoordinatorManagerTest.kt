package com.urbanairship.automation.rewrite.inappmessage.displaycoordinators

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.content.AirshipLayout
import com.urbanairship.automation.rewrite.inappmessage.content.Custom
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DefaultDisplayCoordinator
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinatorManager
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.ImmediateDisplayCoordinator
import com.urbanairship.json.JsonValue
import io.mockk.mockk
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DisplayCoordinatorManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val activityMonitor: InAppActivityMonitor = mockk()
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
        assertTrue(coordinator is ImmediateDisplayCoordinator)
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
}
