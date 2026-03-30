/* Copyright Airship and Contributors */

package com.urbanairship.iam.actions

import android.os.Bundle
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionValue
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.EventAutomationTriggerType
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.push.PushMessage
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.Deflater
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class SceneActionTest {

    @Test
    public fun testAcceptsArguments() {
        val action = SceneAction()

        val valid = listOf(
            Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.Situation.PUSH_OPENED,
            Action.Situation.MANUAL_INVOCATION,
            Action.Situation.WEB_VIEW_INVOCATION,
            Action.Situation.AUTOMATION,
        )
        val rejected = listOf(
            Action.Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.Situation.PUSH_RECEIVED,
        )

        valid.forEach { assertTrue(action.acceptsArguments(ActionArguments(it))) }
        rejected.forEach { assertFalse(action.acceptsArguments(ActionArguments(it))) }
    }

    @Test
    public fun testDefaultNames() {
        assertEquals(setOf("scene_action", "^sla"), SceneAction.DEFAULT_NAMES)
    }

    @Test
    public fun testPerform_schedulesCompressedScene(): TestResult = runTest {
        val scheduleJob = Job()
        val clock = TestClock().apply { currentTimeMillis = 9_876_543_210L }
        val expectedLayout = AirshipLayout.fromJson(JsonValue.parseString(MINIMAL_LAYOUT_JSON))

        val action = SceneAction(
            clock = clock,
            scheduler = { schedule: AutomationSchedule ->
                assertEquals(schedule.productId, "scene_page")
                assertEquals(schedule.queue, "scene_page")
                assertEquals(schedule.priority, Int.MIN_VALUE)
                assertTrue(schedule.bypassHoldoutGroups == true)
                assertEquals(schedule.triggers.size, 1)
                assertEquals(schedule.triggers[0].type, EventAutomationTriggerType.ACTIVE_SESSION.value)
                assertEquals(schedule.triggers[0].goal, 1.0)
                assertEquals(schedule.created, 9_876_543_210UL)

                val message = (schedule.data as AutomationSchedule.ScheduleData.InAppMessageData).message
                assertEquals(
                    InAppMessage(
                        name = "Scene Landing Page ()",
                        displayContent = InAppMessageDisplayContent.AirshipLayoutContent(expectedLayout),
                        isReportingEnabled = false,
                        displayBehavior = InAppMessage.DisplayBehavior.IMMEDIATE,
                    ),
                    message,
                )
                UUID.fromString(schedule.identifier)
                scheduleJob.complete()
            },
        )

        val args = actionArgs(encodedScenePayload(MINIMAL_LAYOUT_JSON))
        assertEquals(ActionResult.Status.COMPLETED, action.perform(args).status)
        scheduleJob.join()
    }

    @Test
    public fun testPerform_messageIdFromRichPush(): TestResult = runTest {
        val scheduleJob = Job()
        val expectedLayout = AirshipLayout.fromJson(JsonValue.parseString(MINIMAL_LAYOUT_JSON))
        val metadata = Bundle().apply {
            putParcelable(
                ActionArguments.PUSH_MESSAGE_METADATA,
                PushMessage(mapOf(PushMessage.EXTRA_RICH_PUSH_ID to "rich-msg-id")),
            )
        }

        val action = SceneAction(
            scheduler = { schedule: AutomationSchedule ->
                assertEquals("rich-msg-id", schedule.identifier)
                val message = (schedule.data as AutomationSchedule.ScheduleData.InAppMessageData).message
                assertTrue(message.isReportingEnabled == true)
                assertEquals("Scene Landing Page (rich-msg-id)", message.name)
                assertEquals(
                    InAppMessageDisplayContent.AirshipLayoutContent(expectedLayout),
                    message.displayContent,
                )
                scheduleJob.complete()
            },
        )

        val args = ActionArguments(
            Action.Situation.MANUAL_INVOCATION,
            ActionValue.wrap(jsonMapOf("scene" to zlibBase64(MINIMAL_LAYOUT_JSON))),
            metadata,
        )
        action.perform(args)
        scheduleJob.join()
    }

    @Test
    public fun testPerform_messageIdFromMetadata(): TestResult = runTest {
        val scheduleJob = Job()
        val metadata = Bundle().apply {
            putString(ActionArguments.RICH_PUSH_ID_METADATA, "metadata-msg-id")
        }

        val action = SceneAction(
            scheduler = { schedule: AutomationSchedule ->
                assertEquals("metadata-msg-id", schedule.identifier)
                scheduleJob.complete()
            },
        )

        val args = ActionArguments(
            Action.Situation.MANUAL_INVOCATION,
            ActionValue.wrap(jsonMapOf("scene" to zlibBase64(MINIMAL_LAYOUT_JSON))),
            metadata,
        )
        action.perform(args)
        scheduleJob.join()
    }

    @Test
    public fun testPerform_scheduleExtender(): TestResult = runTest {
        val scheduleJob = Job()
        val action = SceneAction(
            scheduleExtender = { _, schedule ->
                schedule.copyWith(group = "scene-group")
            },
            scheduler = { schedule: AutomationSchedule ->
                assertEquals("scene-group", schedule.group)
                scheduleJob.complete()
            },
        )

        val args = actionArgs(encodedScenePayload(MINIMAL_LAYOUT_JSON))
        action.perform(args)
        scheduleJob.join()
    }

    @Test
    public fun testPerform_invalidBase64ReturnsError() {
        val action = SceneAction(scheduler = { throw AssertionError("scheduler must not run") })
        val args = ActionArguments(
            Action.Situation.MANUAL_INVOCATION,
            ActionValue.wrap(jsonMapOf("scene" to "@@@not-valid-base64@@@")),
        )
        val result = action.perform(args)
        assertTrue(result is ActionResult.Error)
        assertTrue((result as ActionResult.Error).exception is JsonException)
    }

    @Test
    public fun testPerform_invalidZlibReturnsError() {
        val garbage = Base64.encodeToString(byteArrayOf(0, 1, 2, 3), Base64.DEFAULT)
        val action = SceneAction(scheduler = { throw AssertionError("scheduler must not run") })
        val args = ActionArguments(
            Action.Situation.MANUAL_INVOCATION,
            ActionValue.wrap(jsonMapOf("scene" to garbage)),
        )
        val result = action.perform(args)
        assertTrue(result is ActionResult.Error)
        assertTrue((result as ActionResult.Error).exception is JsonException)
    }

    @Test
    public fun testPerform_decompressedUtf8NotJsonReturnsError() {
        val action = SceneAction(scheduler = { throw AssertionError("scheduler must not run") })
        val args = actionArgs(encodedScenePayload("not json {{{"))
        val result = action.perform(args)
        assertTrue(result is ActionResult.Error)
        assertTrue((result as ActionResult.Error).exception is JsonException)
    }

    @Test
    public fun testPerform_jsonMissingLayoutReturnsError() {
        val action = SceneAction(scheduler = { throw AssertionError("scheduler must not run") })
        val args = actionArgs(encodedScenePayload("""{"version":1}"""))
        val result = action.perform(args)
        assertTrue(result is ActionResult.Error)
        assertTrue((result as ActionResult.Error).exception is JsonException)
    }

    @Test
    public fun testPerform_missingSceneFieldReturnsError() {
        val action = SceneAction(scheduler = { throw AssertionError("scheduler must not run") })
        val args = ActionArguments(
            Action.Situation.MANUAL_INVOCATION,
            ActionValue.wrap(jsonMapOf("other" to "x")),
        )
        val result = action.perform(args)
        assertTrue(result is ActionResult.Error)
        assertTrue((result as ActionResult.Error).exception is JsonException)
    }

    @Test
    public fun testPerform_richPushIdPreferredOverMetadata(): TestResult = runTest {
        val scheduleJob = Job()
        val metadata = Bundle().apply {
            putParcelable(
                ActionArguments.PUSH_MESSAGE_METADATA,
                PushMessage(mapOf(PushMessage.EXTRA_RICH_PUSH_ID to "from-push")),
            )
            putString(ActionArguments.RICH_PUSH_ID_METADATA, "from-metadata")
        }

        val action = SceneAction(
            scheduler = { schedule: AutomationSchedule ->
                assertEquals("from-push", schedule.identifier)
                scheduleJob.complete()
            },
        )

        val args = ActionArguments(
            Action.Situation.MANUAL_INVOCATION,
            ActionValue.wrap(jsonMapOf("scene" to zlibBase64(MINIMAL_LAYOUT_JSON))),
            metadata,
        )
        action.perform(args)
        scheduleJob.join()
    }

    private fun actionArgs(sceneBase64: String): ActionArguments =
        ActionArguments(
            Action.Situation.MANUAL_INVOCATION,
            ActionValue.wrap(jsonMapOf("scene" to sceneBase64)),
        )

    private fun encodedScenePayload(layoutJson: String): String = zlibBase64(layoutJson)

    private fun zlibBase64(json: String): String {
        val compressed = zlibCompress(json.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(compressed, Base64.DEFAULT)
    }

    private fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val buffer = ByteArray(1024)
            ByteArrayOutputStream().use { out ->
                while (!deflater.finished()) {
                    val n = deflater.deflate(buffer)
                    if (n > 0) {
                        out.write(buffer, 0, n)
                    }
                }
                out.toByteArray()
            }
        } finally {
            deflater.end()
        }
    }

    private companion object {
        private val MINIMAL_LAYOUT_JSON = """
            {
              "layout": {
                "version": 1,
                "presentation": {
                  "type": "embedded",
                  "embedded_id": "home_banner",
                  "default_placement": {
                    "size": { "width": "50%", "height": "50%" }
                  }
                },
                "view": { "type": "container", "items": [] }
              }
            }
        """.trimIndent()
    }
}
