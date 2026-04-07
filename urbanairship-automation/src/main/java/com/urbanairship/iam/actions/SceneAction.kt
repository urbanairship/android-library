package com.urbanairship.iam.actions

import androidx.core.os.BundleCompat
import com.urbanairship.Airship
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.inAppAutomation
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.actions.SceneAction.Companion.DEFAULT_NAMES
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField
import com.urbanairship.push.PushMessage
import com.urbanairship.util.Clock
import com.urbanairship.util.base64Decoded
import com.urbanairship.util.rawDeflateInflate
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.runBlocking

/**
 * Scene action that creates and schedules an in-app message with Airship layout content.
 *
 * Accepted situations: [Action.Situation.PUSH_OPENED], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.MANUAL_INVOCATION], [Action.Situation.AUTOMATION],
 * and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 * Accepted argument values: A JSON object containing a "scene" field: UTF-8 layout JSON that was
 * compressed with raw DEFLATE base64 string.
 *
 * Result value: `null`
 *
 * Default Registration Names: [DEFAULT_NAMES]
 */
internal class SceneAction(
    private val scheduler: suspend (AutomationSchedule) -> Unit = {
        Airship.inAppAutomation.upsertSchedules(listOf(it))
    },
    private val clock: Clock = Clock.DEFAULT_CLOCK
) : Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when(arguments.situation) {
            Situation.PUSH_OPENED,
            Situation.WEB_VIEW_INVOCATION,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.MANUAL_INVOCATION,
            Situation.AUTOMATION -> true
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val pushMessage = BundleCompat.getParcelable(
            arguments.metadata,
            ActionArguments.PUSH_MESSAGE_METADATA,
            PushMessage::class.java
        )

        val messageId = pushMessage?.sendId

        val args = try {
            Arguments.fromJson(arguments.value.toJsonValue())
        } catch (e: JsonException) {
            return ActionResult.newErrorResult(e)
        }

        val message = InAppMessage(
            name = "Scene Landing Page (${messageId ?: ""})",
            displayContent = InAppMessageDisplayContent.AirshipLayoutContent(args.scene),
            isReportingEnabled = messageId != null,
            displayBehavior = InAppMessage.DisplayBehavior.IMMEDIATE
        )

        val schedule = AutomationSchedule(
            identifier = messageId ?: UUID.randomUUID().toString(),
            triggers = listOf(AutomationTrigger.activeSession(1u)),
            data = AutomationSchedule.ScheduleData.InAppMessageData(message),
            priority = Int.MIN_VALUE,
            bypassHoldoutGroups = true,
            productId = PRODUCT_ID,
            queue = QUEUE,
            created = clock.currentTimeMillis().toULong()
        )

        runBlocking { scheduler(schedule) }

        return ActionResult.newEmptyResult()
    }

    private data class Arguments(
        val scene: AirshipLayout
    ) {
        companion object {
            private const val KEY_SCENE = "scene"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Arguments {
                val sceneJson = value.requireMap()
                    .requireField<String>(KEY_SCENE)
                    .base64Decoded()
                    ?.rawDeflateInflate()
                    ?.let { String(it, StandardCharsets.UTF_8) }
                    ?: throw JsonException("Invalid scene payload")

                val scene = AirshipLayout.fromJson(JsonValue.parseString(sceneJson))
                return Arguments(scene)
            }
        }
    }

    public companion object {
        private const val PRODUCT_ID = "scene_page"
        // share the same queue as landing page
        private const val QUEUE = "landing_page"

        /** Default registry names. */
        @JvmStatic
        public val DEFAULT_NAMES: Set<String> = setOf("scene_action", "^sla")
    }
}
